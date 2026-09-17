package net.bananemdnsa.historystages.data.lock.spawn;

import net.bananemdnsa.historystages.data.lock.EntitySpawnLockEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Repairs what a hand-edited stage file can get wrong, and says so. Never rejects the entry: a
 * spawn rule with one typo should still lock the rest.
 *
 * <p>The caller passes the id check and the message sink, which keeps this free of Minecraft and of
 * the logger.
 */
public final class SpawnRuleValidator {

    private SpawnRuleValidator() {}

    public static EntitySpawnLockEntry sanitize(EntitySpawnLockEntry entry, Predicate<String> validId,
                                                Consumer<String> warn) {
        String who = "Spawn rule '" + entry.getId() + "'";
        SpawnConditions c = entry.getConditions();
        SpawnConditions fixed = new SpawnConditions(
                filter(c.dimensions(), validId, warn, who, "dimension"),
                filter(c.biomes(), validId, warn, who, "biome"),
                c.sky(),
                c.height(),
                c.time(),
                light(c.light(), warn, who),
                c.weather(),
                moon(c.moonPhases(), warn, who));
        return new EntitySpawnLockEntry(entry.getId(), entry.getLockSources(), entry.getPhase(), fixed,
                extra(entry.getExtraBiomes(), validId, warn, who));
    }

    private static IdFilter filter(IdFilter filter, Predicate<String> validId, Consumer<String> warn,
                                   String who, String kind) {
        if (filter == null) return null;
        List<String> kept = validIds(filter.ids(), validId, warn, who, kind);
        if (kept.isEmpty()) {
            warn.accept(who + ": " + kind + " condition has no valid id left. Removed.");
            return null;
        }
        return kept.size() == filter.ids().size() ? filter : new IdFilter(filter.mode(), kept);
    }

    private static List<String> validIds(List<String> ids, Predicate<String> validId, Consumer<String> warn,
                                         String who, String kind) {
        List<String> kept = new ArrayList<>();
        for (String id : ids) {
            String bare = id.startsWith("#") ? id.substring(1) : id;
            if (validId.test(bare)) {
                kept.add(id);
            } else {
                warn.accept(who + ": " + kind + " '" + id + "' is not a valid id. Removed.");
            }
        }
        return kept;
    }

    private static IntRange light(IntRange light, Consumer<String> warn, String who) {
        if (light == null) return null;
        IntRange clamped = new IntRange(clamp(light.min()), clamp(light.max()));
        if (!clamped.equals(light)) warn.accept(who + ": light " + light.min() + ".." + light.max() + " clamped to 0..15.");
        return clamped;
    }

    private static int clamp(int level) {
        return Math.max(0, Math.min(15, level));
    }

    private static Set<Integer> moon(Set<Integer> phases, Consumer<String> warn, String who) {
        Set<Integer> kept = new TreeSet<>();
        for (int phase : phases) {
            if (phase >= 0 && phase <= 7) {
                kept.add(phase);
            } else {
                warn.accept(who + ": moon phase " + phase + " does not exist (0-7). Removed.");
            }
        }
        return kept;
    }

    private static ExtraBiomeSpawns extra(ExtraBiomeSpawns extra, Predicate<String> validId, Consumer<String> warn,
                                          String who) {
        if (extra == null) return null;
        List<String> kept = validIds(extra.ids(), validId, warn, who, "extra biome");
        return kept.size() == extra.ids().size() ? extra
                : new ExtraBiomeSpawns(kept, extra.weight(), extra.ignoreSpawnRules());
    }
}
