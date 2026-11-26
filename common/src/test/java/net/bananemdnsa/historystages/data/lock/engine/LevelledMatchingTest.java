package net.bananemdnsa.historystages.data.lock.engine;

import java.util.List;
import java.util.Set;

import net.bananemdnsa.historystages.data.LevelledLockEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelledMatchingTest {

    private static final String SHARPNESS = "minecraft:sharpness";
    private static final String POTION = "minecraft:potion";
    private static final String ARROW = "minecraft:tipped_arrow";

    private static List<StackContents.Levelled> carrying(String id, int level) {
        return List.of(new StackContents.Levelled(id, level));
    }

    private static LevelledLockEntry entry(Integer min, List<String> actions, List<String> excluded) {
        return new LevelledLockEntry(SHARPNESS, min, actions, excluded);
    }

    @Test
    void anEntryWithoutAMinimumMatchesEveryLevel() {
        LevelledLockEntry e = new LevelledLockEntry(SHARPNESS);
        assertTrue(LevelledMatching.matches(e, carrying(SHARPNESS, 1)));
        assertTrue(LevelledMatching.matches(e, carrying(SHARPNESS, 5)));
    }

    @Test
    void theMinimumIsInclusiveAndLowerLevelsStayFree() {
        LevelledLockEntry e = entry(4, null, null);
        assertFalse(LevelledMatching.matches(e, carrying(SHARPNESS, 3)));
        assertTrue(LevelledMatching.matches(e, carrying(SHARPNESS, 4)));
    }

    @Test
    void anotherIdNeverMatches() {
        assertFalse(LevelledMatching.matches(new LevelledLockEntry(SHARPNESS), carrying("minecraft:smite", 5)));
    }

    @Test
    void anyCarriedEntryIsEnough() {
        List<StackContents.Levelled> both = List.of(
                new StackContents.Levelled("minecraft:unbreaking", 1),
                new StackContents.Levelled(SHARPNESS, 5));
        assertTrue(LevelledMatching.matches(entry(4, null, null), both));
    }

    /** An entry left with nothing but stations says nothing about the items themselves. */
    @Test
    void onlyStationActionsLeaveItemsFree() {
        LevelledLockEntry stationsOnly = entry(null, List.of("enchanting_table", "anvil"), null);
        assertFalse(LevelledMatching.locksItem(stationsOnly, carrying(SHARPNESS, 5), POTION));
        assertTrue(LevelledMatching.locksItem(entry(null, List.of("pickup"), null),
                carrying(SHARPNESS, 5), POTION));
    }

    /**
     * Actions fixed in the stage settings decide the item path too. Before, the category read the
     * entry's own list only, so fixing "use" as locked never reached a station-only entry.
     */
    @Test
    void fixedActionsDecideTheItemPath() {
        List<String> vocabulary = net.bananemdnsa.historystages.api.lock.LockActions.ENCHANTMENT;
        LevelledLockEntry stationsOnly = entry(null, List.of("enchanting_table", "anvil"), null);
        List<String> useFixedLocked = net.bananemdnsa.historystages.data.lock.FixedLockActions.apply(
                stationsOnly.getLockActions(), java.util.Map.of("use", true), vocabulary);
        assertTrue(LevelledMatching.locksItem(stationsOnly, useFixedLocked, carrying(SHARPNESS, 5), POTION));

        java.util.Map<String, Boolean> allItemActionsFree = new java.util.HashMap<>();
        for (String action : vocabulary) {
            if (!LevelledMatching.STATION_ACTIONS.contains(action)) allItemActionsFree.put(action, false);
        }
        LevelledLockEntry everything = entry(null, null, null);
        List<String> freed = net.bananemdnsa.historystages.data.lock.FixedLockActions.apply(
                everything.getLockActions(), allItemActionsFree, vocabulary);
        assertFalse(LevelledMatching.locksItem(everything, freed, carrying(SHARPNESS, 5), POTION));
    }

    @Test
    void anExcludedItemTypeIsFree() {
        LevelledLockEntry e = entry(null, null, List.of(ARROW));
        assertFalse(LevelledMatching.locksItem(e, carrying(SHARPNESS, 1), ARROW));
        assertTrue(LevelledMatching.locksItem(e, carrying(SHARPNESS, 1), POTION));
    }

    @Test
    void aStationIsRefusedOnlyWhenItsActionIsLocked() {
        LevelledLockEntry e = entry(4, null, null);
        List<String> tableOnly = List.of("enchanting_table");
        assertTrue(LevelledMatching.locksStation(e, tableOnly, SHARPNESS, 4, "enchanting_table"));
        assertFalse(LevelledMatching.locksStation(e, tableOnly, SHARPNESS, 4, "anvil"));
        assertTrue(LevelledMatching.locksStation(e, null, SHARPNESS, 5, "anvil"));
        assertFalse(LevelledMatching.locksStation(e, null, SHARPNESS, 3, "anvil"));
        assertFalse(LevelledMatching.locksStation(e, null, "minecraft:smite", 5, "anvil"));
    }

    @Test
    void modLockNamespacesCoverBooksAndPotionsButNotGear() {
        StackContents contents = new StackContents(
                carrying("gear:enchant", 1),
                carrying("book:enchant", 1),
                carrying("effect:speedy", 1),
                List.of("potion:brew"));
        assertEquals(Set.of("book", "effect", "potion"), contents.namespacesForModLock());
    }

    @Test
    void emptyContentsAreEmpty() {
        assertTrue(StackContents.EMPTY.isEmpty());
        assertFalse(new StackContents(List.of(), List.of(), List.of(), List.of("a:b")).isEmpty());
    }
}
