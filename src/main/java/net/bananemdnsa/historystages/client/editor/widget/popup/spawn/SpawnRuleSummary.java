package net.bananemdnsa.historystages.client.editor.widget.popup.spawn;

import net.bananemdnsa.historystages.data.lock.EntitySpawnLockEntry;
import net.bananemdnsa.historystages.data.lock.spawn.IdFilter;
import net.bananemdnsa.historystages.data.lock.spawn.SpawnConditions;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The dialog's plain-language sentence, as lang keys and arguments. Kept apart from the
 * {@code Component}s so the wording rules can be tested; {@link SummaryComponents} renders it.
 */
public final class SpawnRuleSummary {

    public static final String PREFIX = "editor.historystages.spawn_control.summary.";

    /** Ids joined into one argument; each id translated under {@code langPrefix}, or shown as-is when null. */
    public record Names(List<String> ids, String langPrefix) {}

    /** @param args Integer, String or {@link Names} */
    public record Fragment(String key, List<Object> args) {}

    public record Summary(Fragment phase, Fragment verb, List<Fragment> conditions, Fragment sources, Fragment extra) {}

    private SpawnRuleSummary() {}

    public static Summary describe(EntitySpawnLockEntry entry) {
        SpawnConditions c = entry.getConditions();
        List<Fragment> conditions = new ArrayList<>();
        // Time first: "only at night, in the Overworld" reads better than the other way round.
        if (c.time() != null) conditions.add(plain("cond.time." + c.time().serialize()));
        if (c.dimensions() != null) conditions.add(filter("dimensions", c.dimensions()));
        if (c.biomes() != null) conditions.add(filter("biomes", c.biomes()));
        if (c.sky() != null) conditions.add(plain("cond.sky." + c.sky().serialize()));
        if (c.height() != null) conditions.add(new Fragment(PREFIX + "cond.height", List.of(c.height().min(), c.height().max())));
        if (c.light() != null) conditions.add(new Fragment(PREFIX + "cond.light", List.of(c.light().min(), c.light().max())));
        if (c.weather() != null) conditions.add(plain("cond.weather." + c.weather().serialize()));
        if (!c.moonPhases().isEmpty()) {
            List<String> phases = new TreeSet<>(c.moonPhases()).stream().map(String::valueOf).toList();
            conditions.add(new Fragment(PREFIX + "cond.moon",
                    List.of(new Names(phases, "editor.historystages.spawn_control.moon."))));
        }

        Fragment verb;
        if (!c.isEmpty()) {
            verb = plain("only");
        } else if (entry.getExtraBiomes() != null) {
            verb = plain("unrestricted");
        } else {
            verb = plain("blocked");
        }

        Fragment sources = entry.hasLockSources()
                ? new Fragment(PREFIX + "sources",
                        List.of(new Names(entry.getLockSources(), "editor.historystages.spawn_sources.source.")))
                : null;
        Fragment extra = entry.getExtraBiomes() != null
                ? new Fragment(PREFIX + "extra", List.of(new Names(entry.getExtraBiomes().ids(), null)))
                : null;

        return new Summary(plain("phase." + entry.getPhase().serialize()), verb, conditions, sources, extra);
    }

    private static Fragment plain(String key) {
        return new Fragment(PREFIX + key, List.of());
    }

    private static Fragment filter(String kind, IdFilter filter) {
        return new Fragment(PREFIX + "cond." + kind + "." + filter.mode().serialize(),
                List.of(new Names(filter.ids(), null)));
    }
}
