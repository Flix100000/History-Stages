package net.bananemdnsa.historystages.api.stage;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.StageUnlockedToastPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncIndividualStagesPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStagesPacket;
import net.bananemdnsa.historystages.data.relock.LostStages;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.bananemdnsa.historystages.data.saveddata.TemporaryStageData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.common.MinecraftForge;

import net.bananemdnsa.historystages.data.logic.StageLogicGate;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.HsRuntimeCategories;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

/**
 * Reading and setting stage state — the named place the parent design asks for in §6.3.
 *
 * <p>Everything that unlocks or relocks a stage comes through here: the editor, the research
 * pedestal, {@code /history stage unlock}, an auto-trigger, an FTB Quests reward, and an addon
 * granting a stage of its own. That is not tidiness. Each of these four methods also writes the
 * SavedData, broadcasts the sync packet, clears the structure and biome caches, reloads recipes
 * where the scope calls for it, fires the matching {@link StageEvent}, and sends the player the
 * sound, the chat line and the toast — all of it config-gated.
 *
 * <p>A caller that reproduces an unlock by hand gets a stage that is technically unlocked and
 * visibly broken. That is not hypothetical: {@code ToggleStageLockPacket} had its own copy, the
 * two drifted, and unlocking at a pedestal stopped granting recipes. Commit {@code fcad6ea}
 * repaired it by deleting the copy.
 *
 * <p>Static rather than an interface: there is exactly one implementation and nowhere to inject
 * another, and static entry points are the familiar shape in Minecraft modding. Command callers
 * keep their own command-specific extras — source feedback, debug logging, resource-pack reloads
 * — in the command layer.
 *
 * <p>One of two places under {@code api/} that names NeoForge, and deliberately: firing
 * {@link StageEvent} onto the bus <em>is</em> the loader bridge. See the Phase 9 design §5.
 */
public final class StageStates {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final Set<String> WARNED_UNKNOWN = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private StageStates() {}

    /**
     * What a forced unlock did, for the admin paths that are allowed past a logic block and
     * should say so.
     *
     * @param unlocked   true if the stage was newly unlocked
     * @param wasBlocked true if its logic would have stopped a normal unlock
     */
    public record UnlockOutcome(boolean unlocked, boolean wasBlocked) {}

    /** Every loaded stage definition, global first, then individual. A read-only snapshot. */
    public static List<StageEntry> loadedStages() {
        List<StageEntry> out = new ArrayList<>(StageManager.getStages().values());
        out.addAll(StageManager.getIndividualStages().values());
        return List.copyOf(out);
    }

    /**
     * True if the world has this global stage unlocked. Reads the saved data, so it is correct
     * the moment {@link #unlockGlobal} or {@link #relockGlobal} returns.
     */
    public static boolean isUnlockedGlobal(String stageId, ServerLevel level) {
        return StageData.get(level).hasStage(stageId);
    }

    /**
     * True if this player has the individual stage. A stage shared through a team counts once it
     * has reached this player, which happens as part of the teammate's unlock.
     */
    public static boolean isUnlockedIndividual(String stageId, ServerPlayer player) {
        return IndividualStageData.get(player.serverLevel()).hasStage(player.getUUID(), stageId);
    }

    /**
     * The world's global stages as a {@link StageStateView}, for callers asking about many stages
     * at once. Live: it answers from the saved data on every call, so holding one is safe.
     */
    public static StageStateView globalView(ServerLevel level) {
        StageData data = StageData.get(level);
        return data::hasStage;
    }

    /**
     * One player's individual stages as a {@link StageStateView}. Live like {@link #globalView},
     * so it may be kept across ticks.
     */
    public static StageStateView individualView(ServerPlayer player) {
        IndividualStageData data = IndividualStageData.get(player.serverLevel());
        UUID uuid = player.getUUID();
        return stageId -> data.hasStage(uuid, stageId);
    }

    /**
     * The loaded definition of a stage, or null if no loaded stage has this id. Global first, like
     * {@link #scopeOf}. Treat it as read-only: changes made to it are not saved or synced.
     */
    @Nullable
    public static StageEntry findStage(String stageId) {
        StageEntry entry = StageManager.getStages().get(stageId);
        return entry != null ? entry : StageManager.getIndividualStages().get(stageId);
    }

    /**
     * Which tree a stage id belongs to, or null if no loaded stage has it. An id in both trees
     * counts as global, the way the lock messages have always looked names up.
     */
    @Nullable
    public static StageScope scopeOf(String stageId) {
        if (StageManager.getStages().containsKey(stageId)) return StageScope.GLOBAL;
        if (StageManager.getIndividualStages().containsKey(stageId)) return StageScope.INDIVIDUAL;
        return null;
    }

    /**
     * True if the stage is open for this player in whichever scope it lives in: the world's state
     * for a global stage, the player's own for an individual one. False for an unknown id.
     *
     * <p>For callers that store a bare stage id and should not care which kind it is.
     */
    public static boolean isUnlockedFor(String stageId, ServerPlayer player) {
        StageScope scope = scopeOf(stageId);
        if (scope == null) return false;
        return scope == StageScope.GLOBAL
                ? isUnlockedGlobal(stageId, player.serverLevel())
                : isUnlockedIndividual(stageId, player);
    }

    /**
     * The stage's name as this player may see it. A stage the player does not have yet and whose
     * logic hides it comes back as {@code "???"}, the same as in the mod's own lock messages.
     * Unknown ids come back unchanged.
     *
     * <p>Use it whenever a message names a stage, so an addon never gives away a hidden one.
     */
    public static String displayNameFor(String stageId, ServerPlayer player) {
        return StageLogicGate.nameForAnyScope(stageId, player.getUUID());
    }

    /**
     * False if the stage asks for its lock hints to be hidden ({@code show_lock_hints} in its
     * {@code hidden_display} block). True for an unknown id.
     *
     * <p>A lock hint is the "you still need …" line. When several stages are missing, the mod
     * leaves the hint out as soon as one of them says false, and an addon should do the same.
     */
    public static boolean showsLockHints(String stageId) {
        StageEntry entry = StageManager.getStages().get(stageId);
        if (entry == null) entry = StageManager.getIndividualStages().get(stageId);
        return entry == null || entry.getHiddenDisplay().isShowLockHints();
    }

    /**
     * True if the stage's logic blocks a normal unlock right now: for the world when the stage is
     * global, for this player when it is individual. False for an unknown id.
     */
    public static boolean isBlocked(String stageId, ServerPlayer player) {
        StageScope scope = scopeOf(stageId);
        if (scope == null) return false;
        return scope == StageScope.GLOBAL
                ? StageLogicGate.global(stageId).isBlocked()
                : StageLogicGate.individual(stageId, player.getUUID()).isBlocked();
    }

    /**
     * True if the stage was lost for good by a lock trigger and a normal unlock can no longer
     * bring it back: for the world when global, for this player when individual. Only an admin
     * unlock clears that.
     */
    public static boolean isLostForGood(String stageId, ServerPlayer player) {
        StageScope scope = scopeOf(stageId);
        if (scope == null) return false;
        return scope == StageScope.GLOBAL
                ? LostStages.isLostGlobal(stageId, player.serverLevel())
                : LostStages.isLostIndividual(stageId, player.getUUID(), player.serverLevel());
    }

    /**
     * Where this player stands on the stage's requirements, group by group and entry by entry —
     * what the research pedestal and the graph show. Null for an unknown id.
     *
     * <p>Asked without a scroll, so requirements measured by what was handed in at a pedestal
     * (items, XP deposited) read as not yet met however much was deposited. Everything measured
     * off the player or the world is accurate.
     */
    @Nullable
    public static net.bananemdnsa.historystages.api.dependency.RequirementResult checkRequirements(
            String stageId, ServerPlayer player) {
        StageScope scope = scopeOf(stageId);
        if (scope == null) return null;
        return net.bananemdnsa.historystages.data.dependency.DependencyChecker.checkAll(
                findStage(stageId), player, player.serverLevel(), scope, null);
    }

    /** Every global stage the world has, as a copy. */
    public static Set<String> unlockedGlobal(ServerLevel level) {
        return Set.copyOf(StageData.get(level).getUnlockedStages());
    }

    /** Every individual stage this player has, as a copy. */
    public static Set<String> unlockedIndividual(ServerPlayer player) {
        return Set.copyOf(IndividualStageData.get(player.serverLevel()).getUnlockedStages(player.getUUID()));
    }

    /**
     * When the world unlocked this global stage, in overworld game time ({@code getGameTime()},
     * which {@code /time set} does not move). Empty if it is not unlocked, or was unlocked before
     * the mod started recording times.
     */
    public static OptionalLong unlockedAtGlobal(String stageId, ServerLevel level) {
        Long time = StageData.get(level).getUnlockTimes().get(stageId);
        return time == null ? OptionalLong.empty() : OptionalLong.of(time);
    }

    /** {@link #unlockedAtGlobal} for one player's individual stage. */
    public static OptionalLong unlockedAtIndividual(String stageId, ServerPlayer player) {
        Long time = IndividualStageData.get(player.serverLevel()).getUnlockTimes(player.getUUID()).get(stageId);
        return time == null ? OptionalLong.empty() : OptionalLong.of(time);
    }

    /**
     * Ticks until a temporary global stage locks again. Empty if the stage is not temporary or its
     * timer is not running.
     */
    public static OptionalLong remainingTicksGlobal(String stageId, ServerLevel level) {
        long ticks = TemporaryStageData.get(level).globalActiveTicks(stageId);
        return ticks < 0 ? OptionalLong.empty() : OptionalLong.of(ticks);
    }

    /** {@link #remainingTicksGlobal} for one player's temporary individual stage. */
    public static OptionalLong remainingTicksIndividual(String stageId, ServerPlayer player) {
        long ticks = TemporaryStageData.get(player.serverLevel()).individualActiveTicks(player.getUUID(), stageId);
        return ticks < 0 ? OptionalLong.empty() : OptionalLong.of(ticks);
    }

    /**
     * Unlocks a global stage. No-op if already unlocked, and refused while the stage is blocked
     * by its logic (see {@link StageLogicGate}). Returns true if the stage was newly unlocked.
     *
     * <p>Auto-triggers, scripts and addons come through here and are stopped by a block. Admin
     * paths use {@link #forceUnlockGlobal}.
     */
    public static boolean unlockGlobal(String stageId, ServerLevel level) {
        if (unknown(stageId, StageScope.GLOBAL)) return false;
        if (StageData.get(level).hasStage(stageId)) return false;
        // Throttled: a conditional stage retries every second while its states hold.
        if (LostStages.isLostGlobal(stageId, level)) {
            DebugLogger.runtimeThrottled(HsRuntimeCategories.RELOCK, "refused-lost:" + stageId,
                    "Unlock of '" + stageId + "' refused: lost for good.");
            return false;
        }
        if (StageLogicGate.global(stageId).isBlocked()) {
            DebugLogger.runtimeThrottled(HsRuntimeCategories.STAGE_LOGIC, "refused-logic:" + stageId,
                    "Unlock of '" + stageId + "' refused: blocked by its logic.");
            return false;
        }
        if (vetoed(stageId, StageManager.getStages().get(stageId), null)) return false;
        return applyUnlockGlobal(stageId, level);
    }

    /** Unlocks a global stage even while it is blocked. For commands and the editor. */
    public static UnlockOutcome forceUnlockGlobal(String stageId, ServerLevel level) {
        // An admin unlock is the way back from a permanent loss, so it also forgets the loss.
        LostStages.clearGlobal(stageId, level);
        boolean blocked = StageLogicGate.global(stageId).isBlocked();
        return new UnlockOutcome(applyUnlockGlobal(stageId, level), blocked);
    }

    private static boolean applyUnlockGlobal(String stageId, ServerLevel level) {
        if (unknown(stageId, StageScope.GLOBAL)) return false;
        StageData data = StageData.get(level);
        if (data.hasStage(stageId)) return false;

        data.addStage(stageId);
        data.setDirty();
        StageData.refreshCache(data.getUnlockedStages());

        StageEntry entry = StageManager.getStages().get(stageId);
        String displayName = entry != null ? entry.getDisplayName() : stageId;
        // Logged here rather than by each caller, so research, triggers, scripts and addons all
        // show up — before, only the commands wrote a line.
        DebugLogger.runtime(HsRuntimeCategories.STAGE,
                "Global stage '" + stageId + "' (" + displayName + ") unlocked");

        MinecraftForge.EVENT_BUS.post(new StageEvent.Unlocked(stageId, displayName));

        // Sync the unlocked-stages list to all players
        PacketHandler.sendToAll(new SyncStagesPacket(new ArrayList<>(data.getUnlockedStages())));

        // A stage can opt out of its own messages (conditional stages do by default); the
        // global config switches inside still apply on top.
        if ((entry == null || entry.resolvedNotify()) && !inBatch()
                && announced(List.of(stageId), List.of(displayName), null, true)) {
            broadcastGlobalUnlock(level.getServer(), stageId, displayName, entry);
        }

        invalidateLockCaches();

        MinecraftServer unlockServer = level.getServer();
        if (unlockServer != null) {
            // Recipes gate globally only, so this belongs on the global paths and nowhere else.
            // What it turns into is decided over there: a resend on its own in the normal case,
            // and a full datapack reload only when this change actually altered which recipes are
            // hidden. A machine that reads the entire recipe list and keeps it — Create's basin,
            // and most modded machines — is only told to let go of that copy by a datapack reload,
            // and reloading every datapack is far too expensive to do when nothing needs it.
            PacketHandler.reloadForLockChange(unlockServer);
        }

        return true;
    }

    /**
     * Unlocks an individual stage for the given player. No-op if already unlocked, and refused
     * while the stage is blocked for that player. Returns true if the stage was newly unlocked.
     */
    public static boolean unlockIndividual(String stageId, ServerPlayer player) {
        if (unknown(stageId, StageScope.INDIVIDUAL)) return false;
        if (IndividualStageData.get(player.serverLevel()).hasStage(player.getUUID(), stageId)) return false;
        if (LostStages.isLostIndividual(stageId, player.getUUID(), player.serverLevel())) {
            DebugLogger.runtimeThrottled(HsRuntimeCategories.RELOCK, "refused-lost:" + stageId + ":" + player.getUUID(),
                    "<" + player.getName().getString() + "> Unlock of '" + stageId + "' refused: lost for good.");
            return false;
        }
        if (StageLogicGate.individual(stageId, player.getUUID()).isBlocked()) {
            DebugLogger.runtimeThrottled(HsRuntimeCategories.STAGE_LOGIC, "refused-logic:" + stageId + ":" + player.getUUID(),
                    "<" + player.getName().getString() + "> Unlock of '" + stageId + "' refused: blocked by its logic.");
            return false;
        }
        if (vetoed(stageId, StageManager.getIndividualStages().get(stageId), player.getUUID())) return false;
        return applyUnlockIndividual(stageId, player);
    }

    /**
     * {@link #unlockIndividual(String, ServerPlayer)} for a player who may be offline. An online
     * player gets exactly that. An offline one gets the stage written, the log line and the
     * {@link StageEvent.IndividualUnlocked}; the sync and the messages wait for the next login,
     * where the player's stages are sent anyway.
     */
    public static boolean unlockIndividual(String stageId, UUID playerUuid, MinecraftServer server) {
        ServerPlayer online = server.getPlayerList().getPlayer(playerUuid);
        if (online != null) return unlockIndividual(stageId, online);

        if (unknown(stageId, StageScope.INDIVIDUAL)) return false;
        ServerLevel level = server.overworld();
        IndividualStageData data = IndividualStageData.get(level);
        if (data.hasStage(playerUuid, stageId)) return false;
        if (LostStages.isLostIndividual(stageId, playerUuid, level)) return false;
        if (StageLogicGate.individual(stageId, playerUuid).isBlocked()) return false;
        StageEntry entry = StageManager.getIndividualStages().get(stageId);
        if (vetoed(stageId, entry, playerUuid)) return false;

        data.addStage(playerUuid, stageId);
        data.setDirty();
        String displayName = entry != null ? entry.getDisplayName() : stageId;
        DebugLogger.runtime(HsRuntimeCategories.STAGE,
                "Individual stage '" + stageId + "' (" + displayName + ") unlocked for offline player " + playerUuid);
        MinecraftForge.EVENT_BUS.post(new StageEvent.IndividualUnlocked(stageId, displayName, playerUuid));
        invalidateLockCaches();
        return true;
    }

    /** The counterpart of {@link #unlockIndividual(String, UUID, MinecraftServer)}. */
    public static boolean relockIndividual(String stageId, UUID playerUuid, MinecraftServer server) {
        ServerPlayer online = server.getPlayerList().getPlayer(playerUuid);
        if (online != null) return relockIndividual(stageId, online);

        IndividualStageData data = IndividualStageData.get(server.overworld());
        if (!data.hasStage(playerUuid, stageId)) return false;
        data.removeStage(playerUuid, stageId);
        data.setDirty();
        StageEntry entry = StageManager.getIndividualStages().get(stageId);
        String displayName = entry != null ? entry.getDisplayName() : stageId;
        DebugLogger.runtime(HsRuntimeCategories.STAGE,
                "Individual stage '" + stageId + "' (" + displayName + ") locked again for offline player " + playerUuid);
        MinecraftForge.EVENT_BUS.post(new StageEvent.IndividualLocked(stageId, displayName, playerUuid));
        invalidateLockCaches();
        return true;
    }

    /**
     * True, with a warning, if no loaded stage of this scope has the id.
     *
     * <p>Without it, a typo or an id from the other tree went straight into the saved data: an
     * individual stage unlocked through the global method sat in the world's set, where nothing
     * reads it, and the caller was told it had worked. Relocking stays permissive on purpose, so
     * a stage deleted from the files can still be cleared out of a save.
     */
    private static boolean unknown(String stageId, StageScope scope) {
        boolean known = scope == StageScope.GLOBAL
                ? StageManager.getStages().containsKey(stageId)
                : StageManager.getIndividualStages().containsKey(stageId);
        if (known) return false;
        StageScope actual = scopeOf(stageId);
        String hint = actual == null ? "no loaded stage has this id"
                : "it is " + (actual == StageScope.GLOBAL ? "a global" : "an individual") + " stage";
        // A real warning, once per id: this is a bug in the caller, and the debug log is usually off.
        if (WARNED_UNKNOWN.add(scope + ":" + stageId)) {
            LOGGER.warn("Unlock of '{}' as {} stage refused: {}.", stageId, scope.name().toLowerCase(), hint);
        }
        return true;
    }

    /**
     * Unlocks several global stages as one batch: each goes through {@link #unlockGlobal} with
     * all its checks and its own {@link StageEvent.Unlocked}, but players hear about the batch
     * once, with every name in one message, instead of once per stage. Returns the ids that were
     * newly unlocked, in the order given.
     */
    public static List<String> unlockGlobal(java.util.Collection<String> stageIds, ServerLevel level) {
        List<String> done = new ArrayList<>();
        runBatch(() -> {
            for (String stageId : stageIds) {
                if (unlockGlobal(stageId, level)) done.add(stageId);
            }
        });
        List<StageEntry> loud = noisy(done, StageManager.getStages());
        if (!loud.isEmpty()) {
            List<String> ids = idsOf(loud, done, StageManager.getStages());
            List<String> names = namesOf(loud);
            if (announced(ids, names, null, true)) {
                broadcastGlobalUnlock(level.getServer(), ids.get(0), String.join(", ", names), loud.get(0));
            }
        }
        return done;
    }

    /** {@link #unlockGlobal(java.util.Collection, ServerLevel)} for one player's individual stages. */
    public static List<String> unlockIndividual(java.util.Collection<String> stageIds, ServerPlayer player) {
        List<String> done = new ArrayList<>();
        runBatch(() -> {
            for (String stageId : stageIds) {
                if (unlockIndividual(stageId, player)) done.add(stageId);
            }
        });
        List<StageEntry> loud = noisy(done, StageManager.getIndividualStages());
        if (!loud.isEmpty()) {
            List<String> ids = idsOf(loud, done, StageManager.getIndividualStages());
            List<String> names = namesOf(loud);
            if (announced(ids, names, player.getUUID(), true)) {
                notifyIndividualUnlock(player, ids.get(0), String.join(", ", names), loud.get(0));
            }
        }
        return done;
    }

    /** Depth of batch unlocks on this thread; per-stage messages wait for the batch's own. */
    private static final ThreadLocal<int[]> BATCH = ThreadLocal.withInitial(() -> new int[1]);

    private static boolean inBatch() {
        return BATCH.get()[0] > 0;
    }

    private static void runBatch(Runnable action) {
        int[] depth = BATCH.get();
        depth[0]++;
        try {
            action.run();
        } finally {
            depth[0]--;
        }
    }

    /** The entries among {@code ids} that want to be announced, in order. */
    private static List<StageEntry> noisy(List<String> ids, java.util.Map<String, StageEntry> source) {
        List<StageEntry> out = new ArrayList<>();
        for (String id : ids) {
            StageEntry entry = source.get(id);
            if (entry != null && entry.resolvedNotify()) out.add(entry);
        }
        return out;
    }

    private static List<String> idsOf(List<StageEntry> entries, List<String> candidates,
                                      java.util.Map<String, StageEntry> source) {
        List<String> out = new ArrayList<>();
        for (String id : candidates) {
            if (entries.contains(source.get(id))) out.add(id);
        }
        return out;
    }

    private static List<String> namesOf(List<StageEntry> entries) {
        List<String> out = new ArrayList<>();
        for (StageEntry entry : entries) out.add(entry.getDisplayName());
        return out;
    }

    /** Posts {@link StageAnnounceEvent}; true if the mod should go on and tell the players. */
    private static boolean announced(List<String> ids, List<String> names, @Nullable UUID player, boolean unlock) {
        return !MinecraftForge.EVENT_BUS.post(new StageAnnounceEvent(ids, names, player, unlock));
    }

    /** Posts {@link StageEvent.BeforeUnlock} and reports whether a listener refused. */
    private static boolean vetoed(String stageId, @Nullable StageEntry entry, @Nullable UUID player) {
        String displayName = entry != null ? entry.getDisplayName() : stageId;
        StageEvent.BeforeUnlock event = new StageEvent.BeforeUnlock(stageId, displayName, player);
        MinecraftForge.EVENT_BUS.post(event);
        if (!event.isCanceled()) return false;
        DebugLogger.runtimeThrottled(HsRuntimeCategories.STAGE, "refused-veto:" + stageId + ":" + player,
                "Unlock of '" + stageId + "'" + (player != null ? " for " + player : "") + " refused by an addon.");
        return true;
    }

    /**
     * Unlocks an individual stage even while it is blocked. For commands and the editor, and
     * meant for exactly this player: a stage shared with the team is not passed on from here.
     */
    public static UnlockOutcome forceUnlockIndividual(String stageId, ServerPlayer player) {
        // An admin unlock is the way back from a permanent loss, so it also forgets the loss.
        LostStages.clearIndividual(stageId, player);
        boolean blocked = StageLogicGate.individual(stageId, player.getUUID()).isBlocked();
        boolean unlocked = net.bananemdnsa.historystages.data.team.TeamStageSharing.withoutSharing(
                () -> applyUnlockIndividual(stageId, player));
        return new UnlockOutcome(unlocked, blocked);
    }

    private static boolean applyUnlockIndividual(String stageId, ServerPlayer player) {
        if (unknown(stageId, StageScope.INDIVIDUAL)) return false;
        ServerLevel level = player.serverLevel();
        IndividualStageData data = IndividualStageData.get(level);
        if (data.hasStage(player.getUUID(), stageId)) return false;

        data.addStage(player.getUUID(), stageId);
        data.setDirty();

        StageEntry entry = StageManager.getIndividualStages().get(stageId);
        String displayName = entry != null ? entry.getDisplayName() : stageId;
        DebugLogger.runtime(HsRuntimeCategories.STAGE, player.getName().getString(),
                "Individual stage '" + stageId + "' (" + displayName + ") unlocked");

        // Posted with the player at hand, so a "revoke when" reacting to it reaches this very
        // player object rather than having to look it up by UUID.
        net.bananemdnsa.historystages.events.StageLogicRevocationHandler.withActor(player, () ->
                MinecraftForge.EVENT_BUS.post(new StageEvent.IndividualUnlocked(stageId, displayName, player.getUUID())));

        // Sync the unlocked-stages set to the player
        PacketHandler.sendIndividualStagesToPlayer(
                SyncIndividualStagesPacket.of(data, player.getUUID()),
                player
        );

        // Config-gated chat/actionbar/sound/toast notification to the player
        if ((entry == null || entry.resolvedNotify()) && !inBatch()
                && announced(List.of(stageId), List.of(displayName), player.getUUID(), true)) {
            notifyIndividualUnlock(player, stageId, displayName, entry);
        }

        // Structures and biomes can be gated per player, so their caches go stale here too.
        // Recipes cannot — that category is global-only — so no reload belongs on this path.
        invalidateLockCaches();

        return true;
    }

    /**
     * Re-locks a global stage (used by temporary-mode timer expiry). No-op if
     * already locked. Mirrors the {@code /stage lock} command's side effects:
     * removes the stage, refreshes the cache, syncs to all players, fires the
     * {@link StageEvent.Locked} event, and reloads resources so recipe / JEI
     * gating updates. Returns true if the stage was newly locked.
     */
    public static boolean relockGlobal(String stageId, ServerLevel level) {
        StageData data = StageData.get(level);
        if (!data.hasStage(stageId)) return false;

        data.removeStage(stageId);
        data.setDirty();
        StageData.refreshCache(data.getUnlockedStages());

        StageEntry entry = StageManager.getStages().get(stageId);
        String displayName = entry != null ? entry.getDisplayName() : stageId;
        DebugLogger.runtime(HsRuntimeCategories.STAGE,
                "Global stage '" + stageId + "' (" + displayName + ") locked again");

        MinecraftForge.EVENT_BUS.post(new StageEvent.Locked(stageId, displayName));

        PacketHandler.sendToAll(new SyncStagesPacket(new ArrayList<>(data.getUnlockedStages())));

        // Config-gated chat/actionbar/sound broadcast — same "locked" feedback the
        // /stage lock command produces.
        // A stage can opt out of its own messages (conditional stages do by default); the
        // global config switches inside still apply on top.
        if ((entry == null || entry.resolvedNotify())
                && announced(List.of(stageId), List.of(displayName), null, false)) {
            broadcastGlobalLock(level.getServer(), displayName);
        }

        invalidateLockCaches();

        MinecraftServer server = level.getServer();
        if (server != null) {
            // The same as unlocking, and kept for the same reasons — see the note there. This used
            // to reload every datapack unconditionally; now it only does so when the set of hidden
            // recipes really changed.
            PacketHandler.reloadForLockChange(server);
        }
        return true;
    }

    /**
     * Re-locks an individual stage for the given player (used by temporary-mode
     * timer expiry). No-op if already locked. Removes the stage, syncs to the
     * player, fires {@link StageEvent.IndividualLocked}, and drops any
     * now-locked items from the player's inventory. Returns true if newly locked.
     */
    public static boolean relockIndividual(String stageId, ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        IndividualStageData data = IndividualStageData.get(level);
        if (!data.hasStage(player.getUUID(), stageId)) return false;

        data.removeStage(player.getUUID(), stageId);
        data.setDirty();

        StageEntry entry = StageManager.getIndividualStages().get(stageId);
        String displayName = entry != null ? entry.getDisplayName() : stageId;
        DebugLogger.runtime(HsRuntimeCategories.STAGE, player.getName().getString(),
                "Individual stage '" + stageId + "' (" + displayName + ") locked again");

        net.bananemdnsa.historystages.events.StageLogicRevocationHandler.withActor(player, () ->
                MinecraftForge.EVENT_BUS.post(new StageEvent.IndividualLocked(stageId, displayName, player.getUUID())));

        PacketHandler.sendIndividualStagesToPlayer(
                SyncIndividualStagesPacket.of(data, player.getUUID()),
                player
        );

        net.bananemdnsa.historystages.util.lock.StageLockHelper.dropLockedItemsForPlayer(player, stageId);

        // Config-gated "locked" feedback to the affected player.
        // A stage can opt out of its own messages (conditional stages do by default); the
        // global config switches inside still apply on top.
        if ((entry == null || entry.resolvedNotify())
                && announced(List.of(stageId), List.of(displayName), player.getUUID(), false)) {
            notifyIndividualLock(player, displayName);
        }

        invalidateLockCaches();
        return true;
    }

    /**
     * Drops the structure and biome lock caches so the next tick recomputes them.
     *
     * <p>Both are keyed off which stages are unlocked, and neither notices on its own. Without
     * this the force field around a locked structure stays up until the next chunk scan, which
     * can be minutes — and it used to happen on every path but the editor's, because the editor
     * had these two calls written into its packet handler instead of here.
     *
     * <p>{@code StructureGenerationGate.rebuild()} is deliberately absent: {@code StageData}
     * already calls it from {@code addStage} and {@code removeStage}, so it is covered whichever
     * way the stage moved.
     */
    private static void invalidateLockCaches() {
        net.bananemdnsa.historystages.events.lock.StructureLockHandler.invalidateAll();
        net.bananemdnsa.historystages.events.lock.BiomeLockHandler.invalidateAll();
    }

    // --- private notification helpers ---------------------------------------

    private static void broadcastGlobalLock(MinecraftServer server, String displayName) {
        if (server == null) return;
        if (!Config.VISUAL.broadcastChat.get() && !Config.VISUAL.useActionbar.get()
                && !Config.VISUAL.useSounds.get()) return;

        Component chatMsg = Component.literal("[HistoryStages] ")
                .withStyle(ChatFormatting.RED)
                .append(Component.translatable("message.historystages.stage_forgotten", displayName)
                        .withStyle(ChatFormatting.WHITE));
        Component actionMsg = Component.translatable("message.historystages.stage_locked_action", displayName);

        server.getPlayerList().getPlayers().forEach(player -> {
            if (Config.VISUAL.broadcastChat.get()) {
                player.sendSystemMessage(chatMsg);
            }
            if (Config.VISUAL.useActionbar.get()) {
                player.displayClientMessage(actionMsg, true);
            }
            if (Config.VISUAL.useSounds.get()) {
                player.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.MASTER, 0.75F, 1.0F);
            }
        });
    }

    private static void notifyIndividualLock(ServerPlayer player, String displayName) {
        if (Config.VISUAL.individualBroadcastChat.get()) {
            player.sendSystemMessage(Component.literal("[HistoryStages] ")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.translatable("message.historystages.stage_forgotten", displayName)
                            .withStyle(ChatFormatting.WHITE)));
        }
        if (Config.VISUAL.individualUseActionbar.get()) {
            player.displayClientMessage(Component.translatable("message.historystages.stage_locked_action", displayName), true);
        }
        if (Config.VISUAL.individualUseSounds.get()) {
            player.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.MASTER, 0.75F, 1.0F);
        }
    }

    private static void broadcastGlobalUnlock(MinecraftServer server, String stageId,
                                              String displayName, StageEntry entry) {
        if (server == null) return;
        if (!Config.VISUAL.broadcastChat.get() && !Config.VISUAL.useActionbar.get()
                && !Config.VISUAL.useSounds.get() && !Config.VISUAL.useToasts.get()) return;

        String iconId = (entry != null && !entry.getIcon().isEmpty())
                ? entry.getIcon() : Config.VISUAL.defaultStageIcon.get();

        String rawMsg = Config.VISUAL.unlockMessageFormat.get();
        String formattedMsg = rawMsg.replace("{stage}", displayName).replace("&", "§");
        Component chatMsg = Component.literal("[HistoryStages] ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(formattedMsg));

        Component actionMsg = Component.translatable("message.historystages.new_era_unlocked", displayName);

        server.getPlayerList().getPlayers().forEach(player -> {
            if (Config.VISUAL.broadcastChat.get()) {
                player.sendSystemMessage(chatMsg);
            }
            if (Config.VISUAL.useActionbar.get()) {
                player.displayClientMessage(actionMsg, true);
            }
            if (Config.VISUAL.useSounds.get()) {
                player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.75F, 1.0F);
            }
        });

        if (Config.VISUAL.useToasts.get()) {
            PacketHandler.sendToastToAll(new StageUnlockedToastPacket(displayName, iconId));
        }
    }

    private static void notifyIndividualUnlock(ServerPlayer player, String stageId,
                                               String displayName, StageEntry entry) {
        if (Config.VISUAL.individualBroadcastChat.get()) {
            String configChat = Config.VISUAL.individualUnlockMessageFormat.get();
            String finalChat = configChat.replace("{stage}", displayName)
                    .replace("{player}", player.getName().getString())
                    .replace("&", "§");
            player.sendSystemMessage(
                    Component.literal("[HistoryStages] ")
                            .withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(finalChat))
            );
        }
        if (Config.VISUAL.individualUseActionbar.get()) {
            String configChat = Config.VISUAL.individualUnlockMessageFormat.get();
            String finalChat = configChat.replace("{stage}", displayName)
                    .replace("{player}", player.getName().getString())
                    .replace("&", "§");
            player.displayClientMessage(Component.literal(finalChat), true);
        }
        if (Config.VISUAL.individualUseSounds.get()) {
            player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.75F, 1.0F);
        }
        if (Config.VISUAL.individualUseToasts.get()) {
            String iconId = (entry != null && !entry.getIcon().isEmpty())
                    ? entry.getIcon() : Config.VISUAL.defaultStageIcon.get();
            PacketHandler.sendToastToPlayer(
                    new StageUnlockedToastPacket(displayName, iconId),
                    player
            );
        }
    }
}
