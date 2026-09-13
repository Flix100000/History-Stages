package net.bananemdnsa.historystages.events.lock;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.util.lock.SpawnControlGate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Spawn rules. Maps the vanilla {@link MobSpawnType} to one of our six source buckets and asks
 * {@link SpawnControlGate} whether the spawn may happen; the gate also adds extra-biome spawns and
 * lifts a mob's own placement rules where an entry asks for it.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID)
public class MobSpawnLockHandler {

    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!SpawnControlGate.isActive()) return;
        String entityId = entityId(event.getEntity().getType());
        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        if (!SpawnControlGate.isAllowed(entityId, mapSpawnSource(event.getSpawnType()), event.getLevel(), pos)) {
            event.setSpawnCancelled(true);
            event.setCanceled(true);
        }
    }

    /**
     * Vanilla breeding does not fire {@link FinalizeSpawnEvent} — babies are added directly via
     * {@code Level.addFreshEntity}. NeoForge fires {@link BabyEntitySpawnEvent} instead.
     */
    @SubscribeEvent
    public static void onBabySpawn(BabyEntitySpawnEvent event) {
        if (!SpawnControlGate.isActive()) return;
        if (event.getChild() == null) return;
        if (!(event.getParentA().level() instanceof ServerLevel level)) return;
        String entityId = entityId(event.getChild().getType());
        if (!SpawnControlGate.isAllowed(entityId, "breeding", level, event.getParentA().blockPosition())) {
            event.setCanceled(true);
        }
    }

    /**
     * Fallback for non-Mob entities (items, projectiles, boats, paintings, …) which never go
     * through {@link FinalizeSpawnEvent}. No spawn reason exists here, so every entry applies.
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        // Before anything else: this fires for every arrow, dropped item, XP orb and falling block.
        if (!SpawnControlGate.isActive()) return;
        if (event.getEntity() instanceof Mob) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        String entityId = entityId(event.getEntity().getType());
        if (!SpawnControlGate.isAllowed(entityId, null, level, event.getEntity().blockPosition())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPotentialSpawns(LevelEvent.PotentialSpawns event) {
        if (event.getLevel().isClientSide() || !SpawnControlGate.isActive()) return;
        SpawnControlGate.addExtras(event);
    }

    /** Light, grass, slime chunks and the like. Lifted only inside an extra biome that asks for it. */
    @SubscribeEvent
    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (!SpawnControlGate.isActive() || !isNatural(event.getSpawnType())) return;
        if (SpawnControlGate.forcesPlacement(entityId(event.getEntityType()), event.getLevel(), event.getPos())) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.SUCCEED);
        }
    }

    /**
     * The mob's own {@code checkSpawnRules}. Forcing SUCCEED skips the obstruction check too, so
     * that one is done by hand — "ignore own rules" must never put a mob inside a wall or lava.
     */
    @SubscribeEvent
    public static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (!SpawnControlGate.isActive() || !isNatural(event.getSpawnType())) return;
        Mob mob = event.getEntity();
        if (SpawnControlGate.forcesPlacement(entityId(mob.getType()), event.getLevel(), mob.blockPosition())) {
            event.setResult(mob.checkSpawnObstruction(event.getLevel())
                    ? MobSpawnEvent.PositionCheck.Result.SUCCEED
                    : MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    private static boolean isNatural(MobSpawnType type) {
        return type == MobSpawnType.NATURAL || type == MobSpawnType.CHUNK_GENERATION;
    }

    private static String entityId(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
    }

    private static String mapSpawnSource(MobSpawnType type) {
        return switch (type) {
            case NATURAL, CHUNK_GENERATION -> "natural";
            case SPAWNER, TRIAL_SPAWNER, TRIGGERED, MOB_SUMMONED -> "spawner";
            case STRUCTURE, PATROL, EVENT -> "structure";
            case BREEDING, CONVERSION, REINFORCEMENT, JOCKEY -> "breeding";
            case COMMAND -> "summon";
            case SPAWN_EGG, BUCKET, DISPENSER -> "spawn_egg";
        };
    }
}
