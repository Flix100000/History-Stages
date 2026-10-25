package net.bananemdnsa.historystages.data.lock.engine;

import java.util.List;

import net.bananemdnsa.historystages.data.LevelledLockEntry;
import org.jetbrains.annotations.Nullable;

/**
 * The rules shared by enchantment and effect entries, kept free of Minecraft so they can be
 * pinned by unit tests.
 *
 * <p>Two questions: whether a stack that already carries the thing is locked, and whether a
 * station may produce it. An entry narrowed to station actions only answers no to the first.
 */
public final class LevelledMatching {

    /** The actions that stop a station rather than act on an item. */
    public static final List<String> STATION_ACTIONS = List.of("enchanting_table", "anvil", "brew");

    private LevelledMatching() {}

    /** Whether any carried entry has this id at or above the entry's minimum level. */
    public static boolean matches(LevelledLockEntry entry, List<StackContents.Levelled> carried) {
        for (StackContents.Levelled c : carried) {
            if (levelMatches(entry, c.id(), c.level())) return true;
        }
        return false;
    }

    /**
     * The item path: the entry has to lock at least one item action, must not spare this item
     * type, and the stack has to carry the thing at a locked level.
     */
    public static boolean locksItem(LevelledLockEntry entry, List<StackContents.Levelled> carried,
                                    String itemId) {
        return locksAnyItemAction(entry.getLockActions())
                && !entry.excludes(itemId)
                && matches(entry, carried);
    }

    /**
     * The station path. {@code effectiveActions} is the entry's list with the stage's fixed actions
     * applied — null means every action.
     */
    public static boolean locksStation(LevelledLockEntry entry, @Nullable List<String> effectiveActions,
                                       String id, int level, String station) {
        if (effectiveActions != null && !effectiveActions.contains(station)) return false;
        return levelMatches(entry, id, level);
    }

    private static boolean locksAnyItemAction(@Nullable List<String> actions) {
        if (actions == null) return true;
        for (String action : actions) {
            if (!STATION_ACTIONS.contains(action)) return true;
        }
        return false;
    }

    private static boolean levelMatches(LevelledLockEntry entry, String id, int level) {
        if (!entry.getId().equals(id)) return false;
        Integer min = entry.getMinLevel();
        return min == null || level >= min;
    }
}
