package net.bananemdnsa.historystages.client.cache;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ClientIndividualStageCache {
    private static Set<String> unlockedStages = new HashSet<>();
    private static Map<String, Long> unlockTimes = Map.of();

    /** See {@link ClientStageCache#version()} — same purpose, for the individual set. */
    private static int version;

    public static void setUnlockedStages(Set<String> stages, Map<String, Long> times) {
        unlockedStages = new HashSet<>(stages);
        unlockTimes = times == null ? Map.of() : Map.copyOf(times);
        version++;
    }

    public static boolean isStageUnlocked(String stage) {
        return unlockedStages.contains(stage);
    }

    /** See {@link ClientStageCache#unlockTime(String)}. */
    public static Long unlockTime(String stage) {
        return unlockTimes.get(stage);
    }

    public static void clear() {
        unlockedStages = new HashSet<>();
        unlockTimes = Map.of();
        version++;
    }

    /** Changes whenever the unlocked set is replaced; compare against a previously read value. */
    public static int version() {
        return version;
    }
}
