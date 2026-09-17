package net.bananemdnsa.historystages.client.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientStageCache {
    private static volatile List<String> unlockedStages = new CopyOnWriteArrayList<>();
    private static volatile Map<String, Long> unlockTimes = Map.of();

    /**
     * Bumped on every replacement. Screens that derive state from this cache — the stage graph
     * builds a whole node/edge model from it — have no other way to notice an unlock arriving
     * mid-view, and would otherwise keep drawing a stale picture until reopened.
     */
    private static volatile int version;

    public static void setUnlockedStages(List<String> stages, Map<String, Long> times) {
        unlockedStages = new CopyOnWriteArrayList<>(stages);
        unlockTimes = times == null ? Map.of() : Map.copyOf(times);
        version++;
    }

    public static boolean isStageUnlocked(String stage) {
        return unlockedStages.contains(stage);
    }

    /** Changes whenever the unlocked set is replaced; compare against a previously read value. */
    public static int version() {
        return version;
    }

    /** Game time of the unlock, or null when the stage is locked or predates recorded times. */
    public static Long unlockTime(String stage) {
        return unlockTimes.get(stage);
    }
}
