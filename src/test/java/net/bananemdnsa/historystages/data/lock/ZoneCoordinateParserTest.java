package net.bananemdnsa.historystages.data.lock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * What a pasted coordinate may look like.
 *
 * <p>The F3+C case is the one that matters: it is how a player actually copies a position, and
 * nobody is going to retype three numbers out of a teleport command by hand.
 */
class ZoneCoordinateParserTest {

    @Test
    void readsTheDebugScreenTeleportCommand() {
        ZoneCoordinateParser.Result result = ZoneCoordinateParser.parse(
                "/execute in minecraft:the_nether run tp @s 100.50 64.00 -200.50 -90.00 0.00");

        assertNotNull(result);
        assertEquals(100, result.x());
        assertEquals(64, result.y());
        assertEquals(-201, result.z(), "-200.50 lies in block -201");
        assertEquals("minecraft:the_nether", result.dimension());
    }

    @Test
    void readsThreeBareNumbers() {
        ZoneCoordinateParser.Result result = ZoneCoordinateParser.parse("100 64 200");
        assertEquals(100, result.x());
        assertEquals(64, result.y());
        assertEquals(200, result.z());
        assertNull(result.dimension(), "nothing in the text names a dimension");
    }

    @Test
    void readsCommaSeparatedNumbers() {
        ZoneCoordinateParser.Result result = ZoneCoordinateParser.parse("100, 64, 200");
        assertEquals(100, result.x());
        assertEquals(200, result.z());
    }

    @Test
    void readsSignsAndDecimals() {
        ZoneCoordinateParser.Result result = ZoneCoordinateParser.parse("-1250.5 71 -88");
        assertEquals(-1251, result.x());
        assertEquals(71, result.y());
        assertEquals(-88, result.z());
    }

    /**
     * Truncation towards the block, not towards zero.
     *
     * <p>A player standing at x=-99.1 occupies block -100, and the block is what a zone is
     * measured in. A plain {@code (int)} cast rounds towards zero and would put the corner one
     * block away from where they were standing when they copied it — on the negative side only,
     * which is exactly the sort of thing that gets found months later.
     */
    @Test
    void decimalsAreTruncatedTowardsTheBlockTheyAreIn() {
        assertEquals(99, ZoneCoordinateParser.parse("99.9 64 0").x());
        assertEquals(-100, ZoneCoordinateParser.parse("-99.1 64 0").x());
    }

    @Test
    void tooFewNumbersIsNoResult() {
        assertNull(ZoneCoordinateParser.parse("100 64"));
        assertNull(ZoneCoordinateParser.parse("100"));
        assertNull(ZoneCoordinateParser.parse(""));
        assertNull(ZoneCoordinateParser.parse(null));
    }

    /** Typing a name into a field must not be mistaken for a paste. */
    @Test
    void plainTextWithoutNumbersIsNoResult() {
        assertNull(ZoneCoordinateParser.parse("Krater"));
    }

    /** More than three numbers: the first three win, the rotation from F3+C is ignored. */
    @Test
    void extraNumbersAfterTheFirstThreeAreIgnored() {
        ZoneCoordinateParser.Result result = ZoneCoordinateParser.parse("1 2 3 4 5 6");
        assertEquals(1, result.x());
        assertEquals(2, result.y());
        assertEquals(3, result.z());
    }

    /**
     * A dimension is only believed when it stands before the numbers.
     *
     * <p>Otherwise a version string or a ratio somewhere in the pasted text gets read as one, and
     * the editor silently switches the zone to a world nobody named.
     */
    @Test
    void aColonAfterTheNumbersIsNotADimension() {
        assertNull(ZoneCoordinateParser.parse("100 64 200 ratio 1:8").dimension());
    }
}
