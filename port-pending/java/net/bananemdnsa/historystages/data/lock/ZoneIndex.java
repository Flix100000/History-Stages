package net.bananemdnsa.historystages.data.lock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;

/**
 * Every zone in the pack, grouped by the dimension it sits in.
 *
 * <p>Built once and reused until the stages change. That is the whole reason this class exists:
 * the per-tick question is "which zones could possibly contain this player", and walking every
 * stage of a large pack to answer it would be wasteful when the answer only changes when somebody
 * edits a stage.
 *
 * <p>Note what this is <em>not</em>: it does not know which stages a player has unlocked, and it
 * never asks. Membership is a per-player question answered by the handler; keeping it out of here
 * is what lets one index serve every player on the server.
 *
 * <p>The vertical extent is deliberately absent from {@link Entry#bounds}. A {@code full_height}
 * shape reaches as far as its dimension allows, and the dimension's limits come from a level this
 * class has no access to. Height is settled by the exact test in the handler, where the level is
 * in hand.
 */
public final class ZoneIndex {

    /** One zone, together with the stage that gates it. */
    public record Entry(String stageId, StageScope scope, ZoneEntry zone, int[] bounds) {

        /** The cheap rejection: outside the x/z box, no shape of this zone can contain the point. */
        public boolean couldContain(int x, int z) {
            return ZoneGeometry.withinHorizontalBounds(bounds, x, z);
        }
    }

    private static volatile Map<String, List<Entry>> byDimension = Map.of();
    private static volatile boolean dirty = true;

    /**
     * Whether any zone anywhere suppresses spawns.
     *
     * <p>Worked out once at build time rather than asked per spawn. The spawn hook fires for every
     * mob the world tries to place; a pack with no spawn-blocking zone has to be able to answer
     * "no" without touching a list.
     */
    private static volatile boolean anySpawnBlocking = false;

    private ZoneIndex() {}

    /**
     * Marks the index stale. Raised from the lock engine's {@code stagesChanged}, which every
     * write to the stage store already runs through — toggling, saving, deleting and
     * {@code /history reload} all land there, so no caller needs to remember this one.
     */
    public static void markDirty() {
        dirty = true;
    }

    /** Rebuilds if a stage changed since the last call. Cheap enough to call every tick. */
    public static void rebuildIfDirty() {
        if (!dirty) return;
        // Cleared first: a stage edit arriving mid-rebuild must leave the index dirty rather than
        // be swallowed by a flag cleared afterwards.
        dirty = false;
        rebuild();
    }

    private static void rebuild() {
        Map<String, List<Entry>> built = new HashMap<>();
        collect(built, StageManager.getStages(), StageScope.GLOBAL);
        collect(built, StageManager.getIndividualStages(), StageScope.INDIVIDUAL);
        byDimension = built;

        boolean spawnBlocking = false;
        for (List<Entry> entries : built.values()) {
            for (Entry entry : entries) {
                // Global only: a mob belongs to the world, not to a player, so an individual
                // stage has nothing it could suppress a spawn on behalf of.
                if (entry.scope() != StageScope.GLOBAL) continue;
                if (entry.zone().getRules().isBlockSpawns()) {
                    spawnBlocking = true;
                    break;
                }
            }
            if (spawnBlocking) break;
        }
        anySpawnBlocking = spawnBlocking;
    }

    private static void collect(Map<String, List<Entry>> target,
                                Map<String, StageEntry> stages,
                                StageScope scope) {
        if (stages == null) return;
        for (Map.Entry<String, StageEntry> stage : stages.entrySet()) {
            for (ZoneEntry zone : stage.getValue().getZones()) {
                if (zone == null || !zone.hasShapes()) continue;
                String dimension = zone.getDimension();
                if (dimension == null || dimension.isEmpty()) continue;

                int[] bounds = ZoneGeometry.horizontalBounds(zone.getShapes());
                if (bounds == null) continue;

                target.computeIfAbsent(dimension, d -> new ArrayList<>())
                        .add(new Entry(stage.getKey(), scope, zone, bounds));
            }
        }
    }

    /** The zones in this dimension. Never null; empty when the pack gates nothing there. */
    public static List<Entry> zonesIn(String dimensionId) {
        return byDimension.getOrDefault(dimensionId, List.of());
    }

    /** True when the pack has no zones at all — the coarsest fast-out there is. */
    public static boolean isEmpty() {
        return byDimension.isEmpty();
    }

    /** True when at least one global zone suppresses spawns. See {@link #anySpawnBlocking}. */
    public static boolean anySpawnBlocking() {
        return anySpawnBlocking;
    }

    /** Drops everything. Used when the stage store is cleared before a reload. */
    public static void clear() {
        byDimension = Map.of();
        dirty = true;
    }
}
