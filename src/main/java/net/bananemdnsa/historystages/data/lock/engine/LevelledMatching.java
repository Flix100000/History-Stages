package net.bananemdnsa.historystages.data.lock.engine;

import java.util.List;

import net.bananemdnsa.historystages.data.LevelledLockEntry;

/**
 * The level rule shared by enchantment and effect entries, kept free of Minecraft so it can be
 * pinned by unit tests.
 *
 * <p>Two questions, because {@code lock_items} splits them: whether a stack that already carries
 * the thing is locked, and whether a station may produce it. A station is refused either way.
 */
public final class LevelledMatching {

    private LevelledMatching() {}

    /** Whether any carried entry has this id at or above the entry's minimum level. */
    public static boolean matches(LevelledLockEntry entry, List<StackContents.Levelled> carried) {
        for (StackContents.Levelled c : carried) {
            if (locksStation(entry, c.id(), c.level())) return true;
        }
        return false;
    }

    /** The item path: only when the entry locks items at all. */
    public static boolean locksItem(LevelledLockEntry entry, List<StackContents.Levelled> carried) {
        return entry.isLockItems() && matches(entry, carried);
    }

    /** The station path, which ignores {@code lock_items}. */
    public static boolean locksStation(LevelledLockEntry entry, String id, int level) {
        if (!entry.getId().equals(id)) return false;
        Integer min = entry.getMinLevel();
        return min == null || level >= min;
    }
}
