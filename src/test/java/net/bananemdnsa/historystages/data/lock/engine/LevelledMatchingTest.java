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

    private static List<StackContents.Levelled> carrying(String id, int level) {
        return List.of(new StackContents.Levelled(id, level));
    }

    @Test
    void anEntryWithoutAMinimumMatchesEveryLevel() {
        LevelledLockEntry entry = new LevelledLockEntry(SHARPNESS, null, true);
        assertTrue(LevelledMatching.matches(entry, carrying(SHARPNESS, 1)));
        assertTrue(LevelledMatching.matches(entry, carrying(SHARPNESS, 5)));
    }

    @Test
    void theMinimumIsInclusiveAndLowerLevelsStayFree() {
        LevelledLockEntry entry = new LevelledLockEntry(SHARPNESS, 4, true);
        assertFalse(LevelledMatching.matches(entry, carrying(SHARPNESS, 3)));
        assertTrue(LevelledMatching.matches(entry, carrying(SHARPNESS, 4)));
        assertTrue(LevelledMatching.matches(entry, carrying(SHARPNESS, 5)));
    }

    @Test
    void anotherIdNeverMatches() {
        LevelledLockEntry entry = new LevelledLockEntry(SHARPNESS, null, true);
        assertFalse(LevelledMatching.matches(entry, carrying("minecraft:smite", 5)));
    }

    /** A book with two enchantments is locked as soon as either one is. */
    @Test
    void anyCarriedEntryIsEnough() {
        LevelledLockEntry entry = new LevelledLockEntry(SHARPNESS, 4, true);
        List<StackContents.Levelled> both = List.of(
                new StackContents.Levelled("minecraft:unbreaking", 1),
                new StackContents.Levelled(SHARPNESS, 5));
        assertTrue(LevelledMatching.matches(entry, both));
    }

    @Test
    void lockItemsOffKeepsTheItemFreeButStillLocksTheStation() {
        LevelledLockEntry entry = new LevelledLockEntry(SHARPNESS, null, false);
        assertFalse(LevelledMatching.locksItem(entry, carrying(SHARPNESS, 5)));
        assertTrue(LevelledMatching.locksStation(entry, SHARPNESS, 5));
    }

    @Test
    void theStationRuleHonoursTheMinimum() {
        LevelledLockEntry entry = new LevelledLockEntry(SHARPNESS, 4, true);
        assertFalse(LevelledMatching.locksStation(entry, SHARPNESS, 3));
        assertTrue(LevelledMatching.locksStation(entry, SHARPNESS, 4));
        assertFalse(LevelledMatching.locksStation(entry, "minecraft:smite", 5));
    }

    /**
     * Books and potions count as "from" a mod by what they carry; gear does not, so a vanilla
     * sword with a modded enchantment stays outside that mod's lock.
     */
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
