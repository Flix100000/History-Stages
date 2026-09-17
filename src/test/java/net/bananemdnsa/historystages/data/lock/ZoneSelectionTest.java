package net.bananemdnsa.historystages.data.lock;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two-point marker store.
 *
 * <p>Deliberately free of Minecraft types — a UUID, a dimension name and six numbers is all a
 * selection is, and keeping it that way is what makes these rules provable without a game.
 */
class ZoneSelectionTest {

    private static final UUID PLAYER = UUID.nameUUIDFromBytes("player".getBytes());
    private static final UUID OTHER = UUID.nameUUIDFromBytes("other".getBytes());

    @BeforeEach
    void reset() {
        ZoneSelection.clearAll();
    }

    @Test
    void nothingIsMarkedToBeginWith() {
        assertNull(ZoneSelection.of(PLAYER));
    }

    @Test
    void bothPointsSurviveBeingSet() {
        ZoneSelection.setPoint(PLAYER, 1, 1, 2, 3, "minecraft:overworld");
        ZoneSelection.setPoint(PLAYER, 2, 10, 20, 30, "minecraft:overworld");

        ZoneSelection.Selection selection = ZoneSelection.of(PLAYER);
        assertTrue(selection.isComplete());
        assertEquals(1, selection.firstX());
        assertEquals(2, selection.firstY());
        assertEquals(3, selection.firstZ());
        assertEquals(10, selection.secondX());
        assertEquals(20, selection.secondY());
        assertEquals(30, selection.secondZ());
        assertEquals("minecraft:overworld", selection.dimension());
    }

    /** One point alone is a legal, incomplete state — the editor shows it, it just cannot be used. */
    @Test
    void onePointAloneIsIncomplete() {
        ZoneSelection.setPoint(PLAYER, 1, 0, 0, 0, "minecraft:overworld");

        ZoneSelection.Selection selection = ZoneSelection.of(PLAYER);
        assertNotNull(selection);
        assertFalse(selection.isComplete());
        assertTrue(selection.hasFirst());
        assertFalse(selection.hasSecond());
    }

    /**
     * Marking in another world starts over rather than mixing the two.
     *
     * <p>A box with one corner in the Nether and one in the Overworld describes nothing, and
     * silently keeping the old corner is the sort of thing somebody only notices after saving.
     */
    @Test
    void markingInAnotherDimensionDiscardsTheOldPoint() {
        ZoneSelection.setPoint(PLAYER, 1, 1, 1, 1, "minecraft:overworld");
        ZoneSelection.setPoint(PLAYER, 2, 5, 5, 5, "minecraft:the_nether");

        ZoneSelection.Selection selection = ZoneSelection.of(PLAYER);
        assertEquals("minecraft:the_nether", selection.dimension());
        assertFalse(selection.isComplete(), "the overworld corner must be gone, not carried over");
        assertFalse(selection.hasFirst());
        assertEquals(5, selection.secondX());
    }

    /** The alternating command form: first call sets point 1, next sets point 2, then 1 again. */
    @Test
    void theAlternatingFormCyclesBetweenTheTwoPoints() {
        assertEquals(1, ZoneSelection.nextPoint(PLAYER));
        ZoneSelection.setPoint(PLAYER, 1, 0, 0, 0, "minecraft:overworld");

        assertEquals(2, ZoneSelection.nextPoint(PLAYER));
        ZoneSelection.setPoint(PLAYER, 2, 0, 0, 0, "minecraft:overworld");

        assertEquals(1, ZoneSelection.nextPoint(PLAYER));
    }

    @Test
    void selectionsAreKeptPerPlayer() {
        ZoneSelection.setPoint(PLAYER, 1, 1, 1, 1, "minecraft:overworld");
        assertNull(ZoneSelection.of(OTHER));
    }

    @Test
    void clearRemovesOnlyThatPlayer() {
        ZoneSelection.setPoint(PLAYER, 1, 1, 1, 1, "minecraft:overworld");
        ZoneSelection.setPoint(OTHER, 1, 1, 1, 1, "minecraft:overworld");

        ZoneSelection.clear(PLAYER);
        assertNull(ZoneSelection.of(PLAYER));
        assertNotNull(ZoneSelection.of(OTHER));
    }

    /** Setting the same point twice replaces it rather than adding a third. */
    @Test
    void settingAPointAgainReplacesIt() {
        ZoneSelection.setPoint(PLAYER, 1, 1, 1, 1, "minecraft:overworld");
        ZoneSelection.setPoint(PLAYER, 1, 9, 9, 9, "minecraft:overworld");

        ZoneSelection.Selection selection = ZoneSelection.of(PLAYER);
        assertEquals(9, selection.firstX());
        assertFalse(selection.isComplete(), "replacing point 1 does not invent a point 2");
    }
}
