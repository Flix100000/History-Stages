package net.bananemdnsa.historystages.events.lock;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.client.cache.ClientZoneShapes;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.SyncZoneShapesPacket;
import net.bananemdnsa.historystages.data.lock.ZoneEffectSpec;
import net.bananemdnsa.historystages.data.lock.ZoneEntry;
import net.bananemdnsa.historystages.data.lock.ZoneGeometry;
import net.bananemdnsa.historystages.data.lock.ZoneIndex;
import net.bananemdnsa.historystages.data.lock.ZoneRules;
import net.bananemdnsa.historystages.data.lock.ZoneVerdict;
import net.bananemdnsa.historystages.data.lock.engine.StageLocks;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.bananemdnsa.historystages.util.DebugLogger;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies a zone's own rules to a player standing inside it while its stage is still locked.
 *
 * <p>Structurally the twin of {@link BiomeLockHandler}, with one deliberate difference: there is
 * no check interval and no cell key. A biome lookup touches the chunk, which is why that handler
 * caches its verdict and re-asks only every few ticks; a zone test is a handful of integer
 * comparisons against a prebuilt index, so it runs every tick and reacts immediately.
 *
 * <p>The other difference is where the switches live. Biome and structure locks read theirs from
 * the common config, so every biome behaves alike. A zone carries its own, and a player standing
 * in two at once gets them merged by {@link ZoneVerdict} — highest damage rather than the sum,
 * one message rather than three.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID)
public class ZoneLockHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** How often the lock message repeats, in ticks. Matches the biome handler's cadence. */
    private static final int MESSAGE_INTERVAL = 40;

    private static final Map<UUID, PlayerState> STATE = new HashMap<>();

    /**
     * Dimension ids as strings, kept per level key.
     *
     * <p>{@code dimension().location().toString()} builds a new string every call and this runs
     * per player per tick. There are only ever a handful of dimensions. Same reasoning as
     * {@code MobSpawnLockHandler}.
     */
    private static final Map<ResourceKey<Level>, String> DIMENSION_IDS = new ConcurrentHashMap<>();

    /** Resolved once per effect id; an id nobody can resolve is logged once and remembered. */
    private static final Map<String, Holder<MobEffect>> EFFECT_HOLDERS = new ConcurrentHashMap<>();
    private static final Set<String> UNKNOWN_EFFECTS = ConcurrentHashMap.newKeySet();

    private static class PlayerState {
        int damageCooldown = 0;
        int messageCooldown = 0;
        /** Previous tick's verdict, so leaving a zone can be detected exactly once. */
        boolean wasLocked = false;
        ZoneVerdict verdict = ZoneVerdict.empty();
        /**
         * Every zone in the player's dimension whose stage they are still missing — not only the
         * ones they are standing in. This is what answers "is that block over there locked",
         * which stops a player mining into a zone from just outside its wall.
         */
        List<ZoneIndex.Entry> lockedZones = Collections.emptyList();
        /** The effects applied last tick, so leaving can clear exactly those. */
        List<ZoneEffectSpec> appliedEffects = Collections.emptyList();
        /**
         * Whether the zone that granted those effects wanted them taken off again. Kept here and
         * not read off the verdict: the moment it is needed the player is already out, and the
         * verdict for where they are standing then knows nothing about the zone they left.
         */
        boolean clearOnLeave = false;
        /**
         * The zones the player is standing in, in the order they were handed to the verdict.
         * Lets {@link ZoneVerdict#messageZoneIndex()} name the stage behind the winning message.
         */
        List<ZoneIndex.Entry> zonesHere = Collections.emptyList();
        /** So an inverted barrier too far away to act on complains once, not every tick. */
        boolean haulWarned = false;
        /** What was last pushed to this client, so an unchanged set costs no packet. */
        int lastVisibleSignature = Integer.MIN_VALUE;
        /** Last tick's position, for the movement that was too fast to be seen inside. */
        boolean hasLast = false;
        double lastX;
        double lastY;
        double lastZ;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isSpectator()) return;

        if (!StageLocks.engine().anyZoneLocks()) {
            releaseLeftovers(player);
            return;
        }

        ZoneIndex.rebuildIfDirty();
        if (ZoneIndex.isEmpty()) {
            releaseLeftovers(player);
            return;
        }

        PlayerState state = STATE.computeIfAbsent(player.getUUID(), u -> new PlayerState());
        recompute(player, state);
        syncVisibleZones(player, state);

        ZoneVerdict verdict = state.verdict;
        boolean locked = verdict.isLocked();

        if (state.wasLocked && !locked && state.clearOnLeave) {
            clearEffects(player, state);
        }
        // Read before the assignment below, because leaving is the transition, not the state.
        state.wasLocked = locked;

        if (locked && verdict.barrier()) {
            pushBack(player, state);
        } else if (!locked) {
            catchFlyBy(player, state);
        }
        state.lastX = player.getX();
        state.lastY = player.getY();
        state.lastZ = player.getZ();
        state.hasLast = true;

        if (!locked) return;

        if (!verdict.effects().isEmpty()) {
            applyEffects(player, state, verdict);
        }

        if (verdict.messageEnabled()) {
            state.messageCooldown--;
            if (state.messageCooldown <= 0) {
                state.messageCooldown = MESSAGE_INTERVAL;
                sendLockMessage(player, state, verdict);
            }
        }

        // Creative players are exempt, as they are in the biome and structure handlers: a pack
        // author flying through their own zone to check it should not be killed by it.
        if (verdict.damageEnabled() && !player.isCreative()) {
            state.damageCooldown--;
            if (state.damageCooldown <= 0) {
                state.damageCooldown = verdict.damageInterval();
                player.hurt(player.level().damageSources().magic(), (float) verdict.damageAmount());
            }
        }
    }

    /**
     * Rebuilds both the player's locked-zone list — used to judge arbitrary target positions —
     * and the verdict for where they are standing right now.
     */
    private static void recompute(ServerPlayer player, PlayerState state) {
        List<ZoneIndex.Entry> candidates = ZoneIndex.zonesIn(dimensionId(player.level()));
        if (candidates.isEmpty()) {
            state.lockedZones = Collections.emptyList();
            state.verdict = ZoneVerdict.empty();
            return;
        }

        // Global and individual both. A feature reading only the global map is the gap this mod
        // has grown several times.
        Set<String> playerStages = IndividualStageData.SERVER_CACHE.getOrDefault(
                player.getUUID(), Collections.emptySet());

        List<ZoneIndex.Entry> locked = new ArrayList<>();
        for (ZoneIndex.Entry entry : candidates) {
            if (entry.scope() == StageScope.GLOBAL) {
                if (StageData.SERVER_CACHE.contains(entry.stageId())) continue;
            } else if (playerStages.contains(entry.stageId())) {
                continue;
            }
            locked.add(entry);
        }
        state.lockedZones = locked;

        BlockPos pos = player.blockPosition();
        List<ZoneIndex.Entry> here = zonesAt(state, player.level(), pos);
        state.zonesHere = here;
        if (here.isEmpty()) {
            state.verdict = ZoneVerdict.empty();
            return;
        }

        List<ZoneEntry> zones = new ArrayList<>(here.size());
        for (ZoneIndex.Entry entry : here) zones.add(entry.zone());
        state.verdict = ZoneVerdict.of(zones);
    }

    /** The locked zones containing this position, cheapest test first. */
    private static List<ZoneIndex.Entry> zonesAt(PlayerState state, Level level, BlockPos pos) {
        if (state.lockedZones.isEmpty()) return Collections.emptyList();

        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        List<ZoneIndex.Entry> here = new ArrayList<>(1);
        for (ZoneIndex.Entry entry : state.lockedZones) {
            boolean inverted = entry.zone().getRules().isInverted();

            // The pre-filter only helps the ordinary case. An inverted zone applies everywhere it
            // is not, so a point outside its box is exactly a point it does apply to.
            if (!inverted && !entry.couldContain(pos.getX(), pos.getZ())) continue;

            boolean insideShapes = ZoneGeometry.containsAny(entry.zone().getShapes(),
                    pos.getX(), pos.getY(), pos.getZ(), minY, maxY);
            // The dimension filter stays as it is — it sits in the index and is not part of what
            // gets turned around. An inverted zone means "everywhere in this world except here",
            // never "everywhere in every world".
            if (insideShapes == inverted) continue;

            here.add(entry);
        }
        return here;
    }

    private static String dimensionId(Level level) {
        return DIMENSION_IDS.computeIfAbsent(level.dimension(), key -> key.location().toString());
    }

    // --- What the client is allowed to see ---

    /** How far a zone may be and still be drawn. Beyond this it is not on screen anyway. */
    private static final int VISIBLE_RANGE = 160;

    /** Upper bound on zones pushed at once, so a pack with hundreds cannot burst the packet. */
    private static final int MAX_VISIBLE = 32;

    /**
     * Sends the nearby zones this player has to know about, when that set has changed.
     *
     * <p>A zone reaches a client for one of two reasons, and only those: it asks to be drawn, or
     * it is a barrier. The first keeps the secret — a pack author who wants an area to stay a
     * surprise leaves both switches off and nothing about it crosses the wire, so there is no
     * traffic to read it out of. The second is the price of the barrier feeling like a wall: the
     * client stops the player at it, and it cannot stop them at something it does not know about.
     * A barrier gives itself away the first time anybody walks into it anyway.
     *
     * <p>The rest is distance, which is only about not sending what is nowhere near.
     */
    private static void syncVisibleZones(ServerPlayer player, PlayerState state) {
        int px = player.blockPosition().getX();
        int pz = player.blockPosition().getZ();

        List<ZoneIndex.Entry> candidates = new ArrayList<>();
        for (ZoneIndex.Entry entry : state.lockedZones) {
            ZoneRules rules = entry.zone().getRules();
            if (!rules.isShowBorder() && !rules.isShowOverlay() && !rules.isBarrier()) continue;
            if (horizontalDistance(entry, px, pz) > VISIBLE_RANGE) continue;
            candidates.add(entry);
        }

        // Nearest first, and only once the cap actually bites. Whichever zones fall off the end
        // are the ones nobody can reach yet, and a barrier dropped for sitting late in the list
        // would be a hole in a wall.
        if (candidates.size() > MAX_VISIBLE) {
            candidates.sort(Comparator.comparingDouble(e -> horizontalDistance(e, px, pz)));
            candidates = candidates.subList(0, MAX_VISIBLE);
        }

        List<ClientZoneShapes.Visible> visible = new ArrayList<>(candidates.size());
        for (ZoneIndex.Entry entry : candidates) {
            ZoneRules rules = entry.zone().getRules();
            visible.add(new ClientZoneShapes.Visible(rules.isShowBorder(), rules.isShowOverlay(),
                    rules.isBarrier(), rules.isInverted(), entry.zone().getShapes()));
        }

        int signature = visible.hashCode();
        if (signature == state.lastVisibleSignature) return;
        state.lastVisibleSignature = signature;

        PacketHandler.sendZoneShapes(new SyncZoneShapesPacket(visible), player);
    }

    /**
     * How far this zone is horizontally, or zero for an inverted one — the part of the world it
     * holds is the part outside it, so the player is standing in it unless they are in its shapes.
     */
    private static double horizontalDistance(ZoneIndex.Entry entry, int px, int pz) {
        if (entry.zone().getRules().isInverted()) return 0;

        int[] bounds = entry.bounds();
        if (bounds == null) return Double.MAX_VALUE;

        int dx = Math.max(0, Math.max(bounds[0] - px, px - bounds[1]));
        int dz = Math.max(0, Math.max(bounds[2] - pz, pz - bounds[3]));
        return Math.max(dx, dz);
    }

    // --- Barrier ---

    /**
     * How far an inverted barrier will drag somebody back.
     *
     * <p>A cage put around a starting area after the fact would otherwise haul a player who is
     * halfway across the map, from wherever they happened to log in. Past this distance they are
     * left alone and told instead.
     */
    private static final double MAX_HAUL = 64.0;

    /**
     * Where this player would be pushed to, or null when nothing pushes them.
     *
     * <p>Separate from applying it so the server tests can ask without a fake player being sent a
     * position packet down a connection it does not have.
     */
    public static double[] barrierTargetFor(ServerPlayer player) {
        PlayerState state = STATE.get(player.getUUID());
        if (state == null || state.zonesHere.isEmpty()) return null;

        int minY = player.level().getMinBuildHeight();
        int maxY = player.level().getMaxBuildHeight();

        double[] at = { player.getX(), player.getY(), player.getZ() };
        boolean moved = false;

        for (ZoneIndex.Entry entry : state.zonesHere) {
            if (!entry.zone().getRules().isBarrier()) continue;

            double[] free = ZoneGeometry.escape(entry.zone().getShapes(),
                    entry.zone().getRules().isInverted(), at[0], at[1], at[2], minY, maxY);
            if (free == null) continue;
            at = free;
            moved = true;
        }
        return moved ? at : null;
    }

    /**
     * True when a barrier zone locked for this player covers that position.
     *
     * <p>For the things that put a player somewhere without walking them there — an ender pearl,
     * for one. Refusing the trip beats letting it land and shoving them back a tick later, which
     * would still have put them inside for a moment.
     */
    public static boolean barrierCovers(ServerPlayer player, double x, double y, double z) {
        PlayerState state = STATE.get(player.getUUID());
        if (state == null || state.lockedZones.isEmpty()) return false;

        int minY = player.level().getMinBuildHeight();
        int maxY = player.level().getMaxBuildHeight();
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);

        for (ZoneIndex.Entry entry : state.lockedZones) {
            if (!entry.zone().getRules().isBarrier()) continue;

            boolean inverted = entry.zone().getRules().isInverted();
            if (ZoneGeometry.containsAny(entry.zone().getShapes(), bx, by, bz, minY, maxY)
                    != inverted) {
                return true;
            }
        }
        return false;
    }

    /**
     * Catches the player who crossed a barrier zone between two ticks without ever being seen
     * inside it.
     *
     * <p>With an elytra and a rocket that is twenty blocks of travel per tick — enough to pass
     * clean through a wall, land on the far side, and have every position check agree that
     * nothing happened. Runs only when the player is <em>not</em> currently in a zone, because
     * being in one is the case {@link #pushBack} already owns.
     */
    private static void catchFlyBy(ServerPlayer player, PlayerState state) {
        if (!state.hasLast || state.lockedZones.isEmpty()) return;
        if (player.isCreative() || player.isSpectator()) return;

        int minY = player.level().getMinBuildHeight();
        int maxY = player.level().getMaxBuildHeight();

        for (ZoneIndex.Entry entry : state.lockedZones) {
            if (!entry.zone().getRules().isBarrier()) continue;
            // An inverted zone has no wall to fly through — the whole world outside it is the
            // locked part, and the player is either in it or not.
            if (entry.zone().getRules().isInverted()) continue;

            double[] entered = ZoneGeometry.segmentEnters(entry.zone().getShapes(),
                    state.lastX, state.lastY, state.lastZ,
                    player.getX(), player.getY(), player.getZ(), minY, maxY);
            if (entered == null) continue;

            Entity moving = player.getVehicle() != null ? player.getVehicle() : player;
            moving.teleportTo(state.lastX, state.lastY, state.lastZ);
            moving.setDeltaMovement(Vec3.ZERO);
            if (moving != player) {
                player.connection.teleport(state.lastX, state.lastY, state.lastZ,
                        player.getYRot(), player.getXRot());
            }
            return;
        }
    }

    private static void pushBack(ServerPlayer player, PlayerState state) {
        // A pack author flying through their own zone to check it must not be thrown out of it.
        if (player.isCreative() || player.isSpectator()) return;

        double[] target = barrierTargetFor(player);
        if (target == null) return;

        double dx = target[0] - player.getX();
        double dy = target[1] - player.getY();
        double dz = target[2] - player.getZ();
        if (dx * dx + dy * dy + dz * dz > MAX_HAUL * MAX_HAUL) {
            if (state.haulWarned) return;
            state.haulWarned = true;
            LOGGER.warn("[ZoneLock] {} is {} blocks outside an inverted barrier zone; leaving them "
                            + "where they are rather than dragging them across the world.",
                    player.getName().getString(), (int) Math.sqrt(dx * dx + dy * dy + dz * dz));
            return;
        }
        state.haulWarned = false;

        // The vehicle, not the rider: a horse left behind pulls its rider straight back in on the
        // next tick, and the two would fight each other for as long as the player sits there.
        Entity moving = player.getVehicle() != null ? player.getVehicle() : player;
        moving.teleportTo(target[0], target[1], target[2]);
        moving.setDeltaMovement(Vec3.ZERO);
        if (moving != player) player.connection.teleport(target[0], target[1], target[2],
                player.getYRot(), player.getXRot());
    }

    // --- Effects ---

    /**
     * Applies every effect the verdict names, refreshing an instance only once it has burned
     * through half its duration. Applying every tick would push an effect packet to the client
     * each time; this keeps it to roughly one packet per effect per half-duration.
     */
    private static void applyEffects(ServerPlayer player, PlayerState state, ZoneVerdict verdict) {
        for (ZoneEffectSpec spec : verdict.effects()) {
            Holder<MobEffect> holder = resolve(spec.id());
            if (holder == null) continue;

            MobEffectInstance existing = player.getEffect(holder);
            if (existing != null
                    && existing.getAmplifier() >= spec.amplifier()
                    && existing.getDuration() > spec.durationTicks() / 2) {
                continue;
            }
            player.addEffect(new MobEffectInstance(holder, spec.durationTicks(), spec.amplifier()));
        }
        state.appliedEffects = verdict.effects();
        state.clearOnLeave = verdict.clearEffectsOnLeave();
    }

    /**
     * Clears what this handler applied, not everything the player has.
     *
     * <p>Reading the list off the player's own state rather than off the zone they just left: by
     * the time this runs they are outside, and the zone that granted an effect may already be out
     * of the verdict.
     */
    private static void clearEffects(ServerPlayer player, PlayerState state) {
        for (ZoneEffectSpec spec : state.appliedEffects) {
            Holder<MobEffect> holder = resolve(spec.id());
            if (holder != null) player.removeEffect(holder);
        }
        state.appliedEffects = Collections.emptyList();
        state.clearOnLeave = false;
    }

    /**
     * The other way out of a zone: the zone stops existing while the player is standing in it,
     * because the stage file was edited or the last zone lock was removed.
     *
     * <p>The tick returns above before it ever computes a verdict, so the ordinary leaving path
     * never runs and whatever the zone granted would sit on the player until it expired on its
     * own. Free when nobody is in a zone — the state map is empty then.
     */
    private static void releaseLeftovers(ServerPlayer player) {
        if (STATE.isEmpty()) return;

        PlayerState state = STATE.get(player.getUUID());
        if (state == null) return;

        if (state.clearOnLeave) clearEffects(player, state);

        state.lockedZones = Collections.emptyList();
        // The client is holding shapes it draws and walks into. Nothing else would take them
        // back, and an invisible wall around a zone that no longer exists is unfixable in game.
        syncVisibleZones(player, state);

        // Dropped rather than reset, so the check above is free again for everybody once the last
        // player has been let go. A player who meets a zone later gets a fresh state.
        clearPlayer(player.getUUID());
    }

    /** Null for an id no registry knows, logged once rather than once per tick. */
    private static Holder<MobEffect> resolve(String id) {
        Holder<MobEffect> cached = EFFECT_HOLDERS.get(id);
        if (cached != null) return cached;
        if (UNKNOWN_EFFECTS.contains(id)) return null;

        ResourceLocation location = ResourceLocation.tryParse(id);
        Holder<MobEffect> holder = location == null ? null
                : BuiltInRegistries.MOB_EFFECT.getHolder(location)
                        .map(h -> (Holder<MobEffect>) h)
                        .orElse(null);

        if (holder == null) {
            if (UNKNOWN_EFFECTS.add(id)) {
                LOGGER.warn("[ZoneLock] Skipping effect '{}': unknown effect id", id);
            }
            return null;
        }
        EFFECT_HOLDERS.put(id, holder);
        return holder;
    }

    // --- Messaging ---

    private static void sendLockMessage(ServerPlayer player, PlayerState state,
                                        ZoneVerdict verdict) {
        String zoneName = verdict.messageZoneName();
        String stageName = stageNameForMessage(state, verdict);

        String formatted = verdict.messageText()
                .replace("{zone}", zoneName)
                .replace("{stage}", stageName)
                .replace('&', '§');

        Component message = Component.literal(formatted);
        if (verdict.messageInChat()) {
            player.sendSystemMessage(message);
        } else {
            player.displayClientMessage(message, true);
        }

        DebugLogger.runtime("Zone Lock", player.getName().getString(),
                "Inside locked zone '" + zoneName + "' — missing stage: " + stageName);
    }

    /**
     * The display name of the stage gating the zone whose message won.
     *
     * <p>Found by the index the verdict reports rather than by matching names: nothing stops two
     * zones being called the same thing, and a name match would then credit the wrong stage.
     */
    private static String stageNameForMessage(PlayerState state, ZoneVerdict verdict) {
        int index = verdict.messageZoneIndex();
        if (index < 0 || index >= state.zonesHere.size()) return "";

        String stageId = state.zonesHere.get(index).stageId();
        StageEntry entry = StageManager.getStages().get(stageId);
        if (entry == null) entry = StageManager.getIndividualStages().get(stageId);
        return entry != null ? entry.getDisplayName() : stageId;
    }

    /**
     * The verdict for where this player is standing right now — the same computation the tick
     * runs, minus applying it.
     *
     * <p>Exists for the server tests. Driving the real tick would mean a fake player that ticks,
     * and every effect and message would then try to reach a client that is not there; the test
     * would fail on the packet rather than on the zone. Asking the seam directly is the same
     * arrangement the trade tests use for the same reason.
     */
    public static ZoneVerdict verdictFor(ServerPlayer player) {
        ZoneIndex.rebuildIfDirty();
        PlayerState state = STATE.computeIfAbsent(player.getUUID(), u -> new PlayerState());
        recompute(player, state);
        return state.verdict;
    }

    /** Whether this player may break the block at {@code pos}. The seam behind the break event. */
    public static boolean breakBlocked(ServerPlayer player, BlockPos pos) {
        return shouldBlock(player, false, true, pos);
    }

    public static void clearPlayer(UUID uuid) {
        STATE.remove(uuid);
    }

    // --- Interaction blocking ---

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (shouldBlock(event.getEntity(), true, false, event.getPos())) {
            cancelBlockClick(event);
            return;
        }
        // Placements where the clicked block sits outside but the resulting placement target is
        // inside: buckets, TNT, blocks placed against the border.
        BlockPos placePos = event.getPos().relative(event.getFace());
        if (shouldBlock(event.getEntity(), true, false, placePos)) {
            cancelBlockClick(event);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (shouldBlock(event.getEntity(), true, false, null)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            forceInventoryResync(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (shouldBlock(event.getEntity(), true, false, event.getTarget().blockPosition())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            forceInventoryResync(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (shouldBlock(event.getEntity(), true, false, event.getTarget().blockPosition())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            forceInventoryResync(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (shouldBlock(event.getEntity(), false, true, event.getPos())) {
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (shouldBlock(event.getEntity(), false, true, event.getTarget().blockPosition())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (shouldBlock(event.getPlayer(), false, true, event.getPos())) {
            event.setCanceled(true);
        }
    }

    /**
     * The client already predicted the placement or item consumption locally, and a FAIL result
     * does not reliably undo that — so push the full inventory state back. Same reasoning as the
     * biome and structure locks.
     */
    private static void cancelBlockClick(PlayerInteractEvent.RightClickBlock event) {
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        forceInventoryResync(event.getEntity());
    }

    private static void forceInventoryResync(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.containerMenu.broadcastFullState();
        }
    }

    /**
     * Both halves of the question, asked in turn: does the zone the player is standing in refuse
     * this click, and does the zone they are reaching into refuse it. Either one is enough.
     *
     * <p>The two really can disagree, which is why they are separate. A zone that blocks nothing
     * must not shield a neighbour that does — reaching across the border into a zone is judged by
     * <em>that</em> zone's rules, not by wherever the player's feet happen to be.
     */
    private static boolean shouldBlock(Player player, boolean right, boolean left,
                                       BlockPos targetPos) {
        if (player == null) return false;
        // PlayerInteractEvent fires on both sides. Running this on the client would answer from a
        // state map only the server ever fills, and take the next frame with it.
        if (player.level().isClientSide()) return false;
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        if (serverPlayer.isSpectator()) return false;

        PlayerState state = STATE.get(serverPlayer.getUUID());
        if (state == null) return false;

        if (state.verdict.isLocked()) {
            if (right && state.verdict.blockRightClick()) return true;
            if (left && state.verdict.blockLeftClick()) return true;
        }

        if (targetPos == null) return false;
        for (ZoneIndex.Entry entry : zonesAt(state, serverPlayer.level(), targetPos)) {
            if (right && entry.zone().getRules().isBlockRightClick()) return true;
            if (left && entry.zone().getRules().isBlockLeftClick()) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getProjectile().level().isClientSide()) return;
        if (!(event.getProjectile().getOwner() instanceof ServerPlayer shooter)) return;

        PlayerState state = STATE.get(shooter.getUUID());
        if (state == null || state.lockedZones.isEmpty()) return;

        BlockPos hit = BlockPos.containing(event.getRayTraceResult().getLocation());
        for (ZoneIndex.Entry entry : zonesAt(state, shooter.level(), hit)) {
            if (!entry.zone().getRules().isBlockProjectiles()) continue;
            event.setCanceled(true);
            event.getProjectile().discard();
            return;
        }
    }

    /**
     * Removes blocks inside a locked zone from the explosion's affected list.
     *
     * <p>An explosion has no owner worth attributing, so this asks every tracked player's cached
     * zone list: a zone locked for anyone present is protected. Same compromise the biome and
     * structure handlers make — with the difference that here it is a switch the pack author can
     * turn off per zone, which those two do not offer at all.
     */
    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide()) return;
        if (STATE.isEmpty()) return;

        List<BlockPos> affected = event.getAffectedBlocks();
        if (affected.isEmpty()) return;

        Level level = event.getLevel();
        String dimension = dimensionId(level);

        // One flattened list of the protecting zones, so the per-block loop below does not walk
        // every player's state for every block an explosion touches.
        List<ZoneIndex.Entry> protecting = new ArrayList<>();
        Set<ZoneEntry> seen = Collections.newSetFromMap(new HashMap<>());
        for (PlayerState state : STATE.values()) {
            for (ZoneIndex.Entry entry : state.lockedZones) {
                if (!entry.zone().getRules().isBlockExplosions()) continue;
                if (!dimension.equals(entry.zone().getDimension())) continue;
                if (seen.add(entry.zone())) protecting.add(entry);
            }
        }
        if (protecting.isEmpty()) return;

        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        affected.removeIf(pos -> {
            for (ZoneIndex.Entry entry : protecting) {
                if (!entry.couldContain(pos.getX(), pos.getZ())) continue;
                if (ZoneGeometry.containsAny(entry.zone().getShapes(),
                        pos.getX(), pos.getY(), pos.getZ(), minY, maxY)) {
                    return true;
                }
            }
            return false;
        });
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // Prevents the per-UUID state map from growing forever as players cycle through.
        clearPlayer(event.getEntity().getUUID());
    }
}
