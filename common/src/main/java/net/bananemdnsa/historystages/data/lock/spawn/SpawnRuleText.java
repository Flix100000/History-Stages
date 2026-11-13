package net.bananemdnsa.historystages.data.lock.spawn;

import net.bananemdnsa.historystages.data.lock.EntitySpawnLockEntry;
import net.bananemdnsa.historystages.data.lock.GenerationPhase;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Developer-facing one-liner for the debug dump. Not translated. */
public final class SpawnRuleText {

    private SpawnRuleText() {}

    public static String compact(EntitySpawnLockEntry entry) {
        List<String> parts = new ArrayList<>();
        if (entry.hasLockSources()) parts.add("sources: " + String.join(", ", entry.getLockSources()));
        if (entry.getPhase() == GenerationPhase.AFTER_UNLOCK) parts.add("after_unlock");

        SpawnConditions c = entry.getConditions();
        if (c.dimensions() != null) parts.add("dimensions " + filter(c.dimensions()));
        if (c.biomes() != null) parts.add("biomes " + filter(c.biomes()));
        if (c.sky() != null) parts.add("sky " + c.sky().serialize());
        if (c.height() != null) parts.add("y " + c.height().min() + ".." + c.height().max());
        if (c.time() != null) parts.add("time " + c.time().serialize());
        if (c.light() != null) parts.add("light " + c.light().min() + ".." + c.light().max());
        if (c.weather() != null) parts.add("weather " + c.weather().serialize());
        if (!c.moonPhases().isEmpty()) {
            List<String> phases = new TreeSet<>(c.moonPhases()).stream().map(String::valueOf).toList();
            parts.add("moon " + String.join(",", phases));
        }

        ExtraBiomeSpawns extra = entry.getExtraBiomes();
        if (extra != null) {
            List<String> details = new ArrayList<>();
            if (extra.weight() != null) {
                details.add("weight " + extra.weight().weight());
                details.add("group " + extra.weight().minGroup() + "-" + extra.weight().maxGroup());
            }
            if (extra.ignoreSpawnRules()) details.add("ignoring own rules");
            parts.add("extra biomes: " + String.join(", ", extra.ids())
                    + (details.isEmpty() ? "" : " (" + String.join(", ", details) + ")"));
        }
        return String.join("; ", parts);
    }

    private static String filter(IdFilter filter) {
        return filter.mode().serialize() + " " + String.join(", ", filter.ids());
    }
}
