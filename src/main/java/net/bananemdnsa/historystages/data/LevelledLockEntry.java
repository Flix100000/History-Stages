package net.bananemdnsa.historystages.data;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

/**
 * One gated enchantment or potion effect: a registry id, optionally only from some level up,
 * optionally narrowed to some actions and spared on some item types.
 *
 * <p>Both categories share this shape because they ask the same question of a stack — "does it
 * carry X at level N or higher?". The action list mixes item actions, which apply to stacks that
 * already carry the thing, with station actions, which keep a station from producing it. An entry
 * narrowed to stations only leaves existing items alone.
 */
public class LevelledLockEntry {

    private final String id;

    /** null = every level. */
    @Nullable
    private final Integer minLevel;

    /** null = every action the category offers is locked; empty = none of them is. */
    @Nullable
    private final List<String> lockActions;

    /** Item ids this entry leaves alone, e.g. tipped arrows. null = none. */
    @Nullable
    private final List<String> excludedItemTypes;

    public LevelledLockEntry(String id) {
        this(id, null, null, null);
    }

    public LevelledLockEntry(String id, @Nullable Integer minLevel, @Nullable List<String> lockActions,
                             @Nullable List<String> excludedItemTypes) {
        this.id = id;
        this.minLevel = minLevel != null && minLevel > 1 ? minLevel : null;
        this.lockActions = lockActions != null ? new ArrayList<>(lockActions) : null;
        this.excludedItemTypes = excludedItemTypes != null && !excludedItemTypes.isEmpty()
                ? new ArrayList<>(excludedItemTypes) : null;
    }

    public String getId() { return id; }

    /** The lowest locked level, or null when every level is locked. */
    @Nullable
    public Integer getMinLevel() { return minLevel; }

    /** null if all actions are locked, otherwise the explicit list of locked actions. */
    @Nullable
    public List<String> getLockActions() { return lockActions; }

    @Nullable
    public List<String> getExcludedItemTypes() { return excludedItemTypes; }

    public boolean excludes(String itemId) {
        return excludedItemTypes != null && excludedItemTypes.contains(itemId);
    }

    public LevelledLockEntry withMinLevel(@Nullable Integer level) {
        return new LevelledLockEntry(id, level, lockActions, excludedItemTypes);
    }

    public LevelledLockEntry withLockActions(@Nullable List<String> actions) {
        return new LevelledLockEntry(id, minLevel, actions, excludedItemTypes);
    }

    public LevelledLockEntry withExcludedItemTypes(@Nullable List<String> excluded) {
        return new LevelledLockEntry(id, minLevel, lockActions, excluded);
    }

    public LevelledLockEntry copy() {
        return new LevelledLockEntry(id, minLevel, lockActions, excludedItemTypes);
    }
}
