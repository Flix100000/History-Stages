package net.bananemdnsa.historystages.data;

import org.jetbrains.annotations.Nullable;

/**
 * One gated enchantment or potion effect: a registry id, optionally only from some level up.
 *
 * <p>Both categories share this shape because they ask the same question of a stack — "does it
 * carry X at level N or higher?". {@code lockItems} only means something for enchantments: it
 * decides whether gear already carrying the enchantment is locked, or only the stations that
 * would put it on. An effect entry always locks the potion, since the potion is nothing but its
 * effects.
 */
public class LevelledLockEntry {

    private final String id;

    /** null = every level. */
    @Nullable
    private final Integer minLevel;

    private final boolean lockItems;

    public LevelledLockEntry(String id) {
        this(id, null, true);
    }

    public LevelledLockEntry(String id, @Nullable Integer minLevel, boolean lockItems) {
        this.id = id;
        this.minLevel = minLevel != null && minLevel > 1 ? minLevel : null;
        this.lockItems = lockItems;
    }

    public String getId() { return id; }

    /** The lowest locked level, or null when every level is locked. */
    @Nullable
    public Integer getMinLevel() { return minLevel; }

    public boolean isLockItems() { return lockItems; }

    public LevelledLockEntry copy() {
        return new LevelledLockEntry(id, minLevel, lockItems);
    }
}
