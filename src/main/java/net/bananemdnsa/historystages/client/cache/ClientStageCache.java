package net.bananemdnsa.historystages.client.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ClientStageCache {
    private static List<String> unlockedStages = new ArrayList<>();
    private static Map<String, Long> unlockTimes = Map.of();

    /**
     * Bumped on every replacement. Screens that derive state from this cache — the stage graph
     * builds a whole node/edge model from it — have no other way to notice an unlock arriving
     * mid-view, and would otherwise keep drawing a stale picture until reopened.
     */
    private static int version;

    public static void setUnlockedStages(List<String> stages, Map<String, Long> times) {
        unlockedStages = stages;
        unlockTimes = times == null ? Map.of() : Map.copyOf(times);
        version++;
    }

    /** Changes whenever the unlocked set is replaced; compare against a previously read value. */
    public static int version() {
        return version;
    }

    // Diese Methode wird jetzt vom Screen aufgerufen
    public static boolean isStageUnlocked(String stage) {
        return unlockedStages.contains(stage);
    }

    /** Game time of the unlock, or null when the stage is locked or predates recorded times. */
    public static Long unlockTime(String stage) {
        return unlockTimes.get(stage);
    }


}
