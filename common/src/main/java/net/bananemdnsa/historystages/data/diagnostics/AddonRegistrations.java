package net.bananemdnsa.historystages.data.diagnostics;

import net.bananemdnsa.historystages.util.DebugLogger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Which mod registered what through the API, so the debug report can list the addons in play
 * and how many problems each one caused.
 *
 * <p>Pure: the active mod and the "is this a loaded mod" question come in from outside, so it can
 * be unit-tested without FML.
 */
public final class AddonRegistrations {

    private static final String HS = "historystages";

    private record Registration(@Nullable String activeMod, String id, ApiExtension what) {}

    /** What one addon registered and how many errors and warnings its checks and sections caused. */
    public record AddonSummary(String modId, Map<ApiExtension, Integer> registered, int errors, int warnings) {}

    private static final List<Registration> ALL = new ArrayList<>();
    /**
     * {@code <what>|<id>} → {errors, warnings} for the report being built. Keyed by kind as well,
     * because one id may name a category, a check and a section at once.
     */
    private static final Map<String, int[]> PROBLEMS = new HashMap<>();

    private AddonRegistrations() {}

    public static synchronized void record(@Nullable String activeMod, String id, ApiExtension what) {
        ALL.add(new Registration(activeMod, id, what));
    }

    public static synchronized void problem(ApiExtension what, String registrationId, DebugLogger.Level level) {
        if (level == DebugLogger.Level.INFO) return;
        int[] counts = PROBLEMS.computeIfAbsent(what + "|" + registrationId, k -> new int[2]);
        counts[level == DebugLogger.Level.ERROR ? 0 : 1]++;
    }

    public static synchronized void clearProblems() {
        PROBLEMS.clear();
    }

    public static synchronized List<AddonSummary> summaries(Predicate<String> isLoadedMod) {
        Map<String, Map<ApiExtension, Integer>> registered = new LinkedHashMap<>();
        Map<String, int[]> problems = new HashMap<>();
        for (Registration r : ALL) {
            String owner = owner(r.activeMod(), r.id(), isLoadedMod);
            if (owner == null) continue;
            registered.computeIfAbsent(owner, k -> new EnumMap<>(ApiExtension.class)).merge(r.what(), 1, Integer::sum);
            int[] p = PROBLEMS.get(r.what() + "|" + r.id());
            if (p != null) {
                int[] sum = problems.computeIfAbsent(owner, k -> new int[2]);
                sum[0] += p[0];
                sum[1] += p[1];
            }
        }
        List<AddonSummary> out = new ArrayList<>();
        for (Map.Entry<String, Map<ApiExtension, Integer>> e : registered.entrySet()) {
            int[] p = problems.getOrDefault(e.getKey(), new int[2]);
            out.add(new AddonSummary(e.getKey(), e.getValue(), p[0], p[1]));
        }
        return out;
    }

    /**
     * The mod a registration belongs to, or null when it is History Stages' own.
     *
     * <p>Mostly the mod whose listener was running. The exception is an addon bundled inside the
     * HS jar, such as the demo addon: FML reports HS for it, so it is recognised by an id namespace
     * that no loaded mod owns. A foreign namespace that is a loaded mod — HS registering
     * {@code ftbteams:party} itself — stays HS's.
     */
    static @Nullable String owner(@Nullable String activeMod, String id, Predicate<String> isLoadedMod) {
        if (activeMod == null) return null;
        if (!HS.equals(activeMod)) return activeMod;
        int colon = id == null ? -1 : id.indexOf(':');
        if (colon <= 0) return null;
        String namespace = id.substring(0, colon);
        if (namespace.equals(HS) || isLoadedMod.test(namespace)) return null;
        return namespace;
    }

    public static synchronized void resetForTest() {
        ALL.clear();
        PROBLEMS.clear();
    }
}
