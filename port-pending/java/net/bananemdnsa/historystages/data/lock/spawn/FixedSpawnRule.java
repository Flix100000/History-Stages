package net.bananemdnsa.historystages.data.lock.spawn;

import net.bananemdnsa.historystages.data.lock.EntitySpawnLockEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * SpawnControl rows a stage fixes for every spawn-lock entry. A fixed row takes the stage's value
 * on every mob, whatever the mob says; the other rows stay the mob's own.
 *
 * <p>A row is one line of the SpawnControl dialog with everything that hangs off it: "dimensions"
 * is the mode and its id list, "height" the switch and its range. Each spawn source is a row of
 * its own, the way each lock action is. Extra biomes are never fixed — they belong to one mob.
 *
 * <p>{@link #values} holds the stage's value for every row, fixed or not; only the fixed ones are
 * ever read.
 */
public final class FixedSpawnRule {

    public static final String PHASE = "phase";
    public static final String DIMENSIONS = "dimensions";
    public static final String BIOMES = "biomes";
    public static final String SKY = "sky";
    public static final String HEIGHT = "height";
    public static final String TIME = "time";
    public static final String LIGHT = "light";
    public static final String WEATHER = "weather";
    public static final String MOON = "moon";

    /** Every row that can be fixed, in dialog order. */
    public static final List<String> ROWS = allRows();

    private final Set<String> rows;
    private final EntitySpawnLockEntry values;

    public FixedSpawnRule(Collection<String> rows, EntitySpawnLockEntry values) {
        Set<String> known = new LinkedHashSet<>();
        for (String row : ROWS) {
            if (rows.contains(row)) known.add(row);
        }
        this.rows = Set.copyOf(known);
        this.values = values == null ? new EntitySpawnLockEntry("*")
                : new EntitySpawnLockEntry("*", null, values.getPhase(), values.getConditions(), null)
                        .withLockedSources(lockedSources(values));
    }

    /** The sources this entry blocks, spelled out — "none" stays an empty list. */
    public static List<String> lockedSources(EntitySpawnLockEntry entry) {
        return EntitySpawnLockEntry.ALL_SOURCES.stream().filter(entry::blocksSource).toList();
    }

    public static String source(String source) {
        return "source." + source;
    }

    /** The fixed rows, in dialog order. */
    public List<String> rows() {
        return ROWS.stream().filter(rows::contains).toList();
    }

    public boolean isFixed(String row) {
        return rows.contains(row);
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    /** The stage's value for every row, under the placeholder id {@code *}. */
    public EntitySpawnLockEntry values() {
        return values;
    }

    /** The entry as it really applies: its own rows, with the fixed ones set to the stage's value. */
    public EntitySpawnLockEntry apply(EntitySpawnLockEntry entry) {
        return copyRows(rows, values, entry);
    }

    /**
     * {@code onto} with the named rows taken from {@code from}. Id and extra biomes always stay
     * those of {@code onto}.
     */
    public static EntitySpawnLockEntry copyRows(Set<String> rows, EntitySpawnLockEntry from, EntitySpawnLockEntry onto) {
        if (rows.isEmpty()) return onto;
        SpawnConditions f = from.getConditions();
        SpawnConditions o = onto.getConditions();
        SpawnConditions conditions = new SpawnConditions(
                rows.contains(DIMENSIONS) ? f.dimensions() : o.dimensions(),
                rows.contains(BIOMES) ? f.biomes() : o.biomes(),
                rows.contains(SKY) ? f.sky() : o.sky(),
                rows.contains(HEIGHT) ? f.height() : o.height(),
                rows.contains(TIME) ? f.time() : o.time(),
                rows.contains(LIGHT) ? f.light() : o.light(),
                rows.contains(WEATHER) ? f.weather() : o.weather(),
                rows.contains(MOON) ? f.moonPhases() : o.moonPhases());

        // Unchanged sources must not turn a stored entry into a "none locked" one: copying
        // only conditions over an ordinary entry has to give an ordinary entry back.
        List<String> locked = new ArrayList<>();
        for (String source : EntitySpawnLockEntry.ALL_SOURCES) {
            boolean blocks = rows.contains(source(source)) ? from.blocksSource(source) : onto.blocksSource(source);
            if (blocks) locked.add(source);
        }

        return new EntitySpawnLockEntry(onto.getId(), null,
                rows.contains(PHASE) ? from.getPhase() : onto.getPhase(),
                conditions, onto.getExtraBiomes())
                .withLockedSources(locked);
    }

    private static List<String> allRows() {
        List<String> all = new ArrayList<>();
        all.add(PHASE);
        for (String source : EntitySpawnLockEntry.ALL_SOURCES) all.add(source(source));
        all.addAll(List.of(DIMENSIONS, BIOMES, SKY, HEIGHT, TIME, LIGHT, WEATHER, MOON));
        return List.copyOf(all);
    }
}
