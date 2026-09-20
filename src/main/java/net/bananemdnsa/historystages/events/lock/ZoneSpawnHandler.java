package net.bananemdnsa.historystages.events.lock;

import java.util.HashSet;
import java.util.Set;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.data.lock.ZoneGeometry;
import net.bananemdnsa.historystages.data.lock.ZoneIndex;
import net.bananemdnsa.historystages.data.lock.engine.StageLocks;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import org.slf4j.Logger;

/**
 * Keeps a locked zone empty of mobs until its stage is unlocked.
 *
 * <p>Hooked on the position check rather than on spawn finalisation, which is where the mod's
 * other spawn gate sits. The difference is the whole point: the position check runs while the
 * world is looking for somewhere to put a mob, so refusing there stops natural spawning and
 * leaves spawners, spawn eggs and commands alone. Somebody placing a mob on purpose means it.
 *
 * <p><strong>Global stages only.</strong> A mob belongs to the world, not to a player. On an
 * individual stage it would have to exist for one player and not for the next, and there is no
 * such thing — the same reason the structure generation rules are global-only.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID)
public final class ZoneSpawnHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Stages already warned about, so a misconfigured pack logs once and not once per spawn. */
    private static final Set<String> WARNED = new HashSet<>();

    private ZoneSpawnHandler() {}

    @SubscribeEvent
    public static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (!StageLocks.engine().anyZoneLocks()) return;
        ZoneIndex.rebuildIfDirty();
        if (!ZoneIndex.anySpawnBlocking()) return;
        if (!isNatural(event.getSpawnType())) return;

        Level level = event.getLevel().getLevel();
        String dimension = level.dimension().location().toString();

        int x = (int) Math.floor(event.getX());
        int y = (int) Math.floor(event.getY());
        int z = (int) Math.floor(event.getZ());
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        for (ZoneIndex.Entry entry : ZoneIndex.zonesIn(dimension)) {
            if (!entry.zone().getRules().isBlockSpawns()) continue;

            if (entry.scope() != StageScope.GLOBAL) {
                warnOnce(entry.stageId());
                continue;
            }
            if (StageData.SERVER_CACHE.contains(entry.stageId())) continue;

            boolean inverted = entry.zone().getRules().isInverted();
            if (!inverted && !entry.couldContain(x, z)) continue;
            if (ZoneGeometry.containsAny(entry.zone().getShapes(), x, y, z, minY, maxY) == inverted) {
                continue;
            }

            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
            return;
        }
    }

    /**
     * Only what the world places by itself.
     *
     * <p>Chunk generation counts: the mobs a fresh chunk arrives with are as natural as the ones
     * that appear at night, and letting them through would leave a newly generated zone populated
     * while an old one stays empty.
     */
    private static boolean isNatural(MobSpawnType type) {
        return type == MobSpawnType.NATURAL || type == MobSpawnType.CHUNK_GENERATION;
    }

    private static void warnOnce(String stageId) {
        if (!WARNED.add(stageId)) return;
        LOGGER.warn("[ZoneLock] Stage '{}' is individual and asks a zone to block spawns. "
                + "A spawn belongs to the world, not to one player, so the switch is ignored.",
                stageId);
    }
}
