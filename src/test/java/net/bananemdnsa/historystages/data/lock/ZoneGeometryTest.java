package net.bananemdnsa.historystages.data.lock;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The point-in-shape maths, proven without a running game.
 *
 * <p>Build limits are handed in rather than looked up, which is what keeps the class under test
 * free of Minecraft: the Overworld's -64..320 is not the Nether's, and a modded dimension is
 * neither.
 */
class ZoneGeometryTest {

    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;

    // ---------------------------------------------------------------------------------------
    // Surface blocks. Held against a brute-force sweep rather than against hand-written
    // expectations: the whole point of the fast version is that nobody can read a sphere off a
    // page, and an enumeration that quietly misses a block leaves a hole in a wall that only
    // shows up in game, from one particular angle.
    // ---------------------------------------------------------------------------------------

    private static Set<String> surfaceByBruteForce(ZoneShape shape,
                                                   int x0, int y0, int z0,
                                                   int x1, int y1, int z1) {
        Set<String> found = new LinkedHashSet<>();
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    if (!ZoneGeometry.contains(shape, x, y, z, MIN_Y, MAX_Y)) continue;
                    if (ZoneGeometry.contains(shape, x - 1, y, z, MIN_Y, MAX_Y)
                            && ZoneGeometry.contains(shape, x + 1, y, z, MIN_Y, MAX_Y)
                            && ZoneGeometry.contains(shape, x, y - 1, z, MIN_Y, MAX_Y)
                            && ZoneGeometry.contains(shape, x, y + 1, z, MIN_Y, MAX_Y)
                            && ZoneGeometry.contains(shape, x, y, z - 1, MIN_Y, MAX_Y)
                            && ZoneGeometry.contains(shape, x, y, z + 1, MIN_Y, MAX_Y)) {
                        continue;
                    }
                    found.add(x + "/" + y + "/" + z);
                }
            }
        }
        return found;
    }

    private static Set<String> surfaceByEnumeration(ZoneShape shape,
                                                    int x0, int y0, int z0,
                                                    int x1, int y1, int z1) {
        Set<String> found = new LinkedHashSet<>();
        ZoneGeometry.surfaceBlocks(shape, x0, y0, z0, x1, y1, z1, MIN_Y, MAX_Y,
                (x, y, z) -> found.add(x + "/" + y + "/" + z));
        return found;
    }

    private static void assertSurfaceMatches(ZoneShape shape,
                                             int x0, int y0, int z0, int x1, int y1, int z1) {
        Set<String> expected = surfaceByBruteForce(shape, x0, y0, z0, x1, y1, z1);
        Set<String> actual = surfaceByEnumeration(shape, x0, y0, z0, x1, y1, z1);

        Set<String> missed = new LinkedHashSet<>(expected);
        missed.removeAll(actual);
        Set<String> extra = new LinkedHashSet<>(actual);
        extra.removeAll(expected);

        assertTrue(missed.isEmpty(), "surface blocks the enumeration never offered: " + missed);
        assertTrue(extra.isEmpty(), "blocks offered that are not on the surface: " + extra);
        assertFalse(expected.isEmpty(), "the sweep found no surface at all, so this proves nothing");
    }

    @Test
    void everySurfaceBlockOfASphereIsFound() {
        assertSurfaceMatches(ZoneShape.sphere(0, 70, 0, 9), -12, 58, -12, 12, 82, 12);
    }

    /** An even radius puts blocks exactly on the boundary, where a rounded root goes wrong. */
    @Test
    void everySurfaceBlockOfASphereWithAWholeNumberEdgeIsFound() {
        assertSurfaceMatches(ZoneShape.sphere(3, 70, -5, 8), -8, 60, -16, 14, 80, 6);
    }

    @Test
    void everySurfaceBlockOfACylinderIsFound() {
        assertSurfaceMatches(ZoneShape.cylinder(0, 64, 0, 7, 9, false), -10, 60, -10, 10, 78, 10);
    }

    @Test
    void everySurfaceBlockOfACubeIsFound() {
        assertSurfaceMatches(ZoneShape.cube(-4, 62, -3, 6, 71, 8, false), -8, 58, -8, 10, 76, 12);
    }

    /** A box off to one side has to answer for the part of it that is in view, and nothing else. */
    @Test
    void aBoxThatOnlyPartlyOverlapsIsCutToIt() {
        assertSurfaceMatches(ZoneShape.sphere(0, 70, 0, 9), 0, 64, 0, 20, 84, 20);
    }

    @Test
    void aFullHeightShapeTakesItsEndsFromTheDimension() {
        ZoneShape tower = ZoneShape.cylinder(0, 0, 0, 5, 0, true);
        Set<String> found = surfaceByEnumeration(tower, -8, MIN_Y, -8, 8, MIN_Y + 4, 8);

        assertTrue(found.contains("0/" + MIN_Y + "/0"),
                "the floor of a full-height shape is at the bottom of the dimension");
        assertFalse(found.contains("0/" + (MIN_Y + 2) + "/0"),
                "the middle of the floor is not a surface once you are off the bottom");
    }

    @Test
    void cubeContainsPointInside() {
        ZoneShape cube = ZoneShape.cube(0, 0, 0, 10, 10, 10, false);
        assertTrue(ZoneGeometry.contains(cube, 5, 5, 5, MIN_Y, MAX_Y));
    }

    @Test
    void cubeContainsItsCorners() {
        ZoneShape cube = ZoneShape.cube(0, 0, 0, 10, 10, 10, false);
        assertTrue(ZoneGeometry.contains(cube, 0, 0, 0, MIN_Y, MAX_Y), "lower corner is inside");
        assertTrue(ZoneGeometry.contains(cube, 10, 10, 10, MIN_Y, MAX_Y), "upper corner is inside");
    }

    /** A zone from 100 to 100 is one block, not empty. */
    @Test
    void cubeOfOneBlockContainsThatBlock() {
        ZoneShape cube = ZoneShape.cube(100, 64, 100, 100, 64, 100, false);
        assertTrue(ZoneGeometry.contains(cube, 100, 64, 100, MIN_Y, MAX_Y));
    }

    @Test
    void cubeExcludesPointOutside() {
        ZoneShape cube = ZoneShape.cube(0, 0, 0, 10, 10, 10, false);
        assertFalse(ZoneGeometry.contains(cube, 11, 5, 5, MIN_Y, MAX_Y));
        assertFalse(ZoneGeometry.contains(cube, 5, -1, 5, MIN_Y, MAX_Y));
    }

    /** Corners in any order: whoever types 100 and 50 means 50 to 100. */
    @Test
    void cubeAcceptsReversedCorners() {
        ZoneShape cube = ZoneShape.cube(10, 10, 10, 0, 0, 0, false);
        assertTrue(ZoneGeometry.contains(cube, 5, 5, 5, MIN_Y, MAX_Y));
    }

    @Test
    void sphereContainsItsCentreAndSurface() {
        ZoneShape sphere = ZoneShape.sphere(0, 64, 0, 10);
        assertTrue(ZoneGeometry.contains(sphere, 0, 64, 0, MIN_Y, MAX_Y), "centre");
        assertTrue(ZoneGeometry.contains(sphere, 10, 64, 0, MIN_Y, MAX_Y), "exactly on the radius");
        assertFalse(ZoneGeometry.contains(sphere, 11, 64, 0, MIN_Y, MAX_Y), "one past it");
    }

    /** A whole sphere, deliberately not cut off below — see the spec, section 4. */
    @Test
    void sphereReachesBelowItsCentre() {
        ZoneShape sphere = ZoneShape.sphere(0, 64, 0, 10);
        assertTrue(ZoneGeometry.contains(sphere, 0, 56, 0, MIN_Y, MAX_Y));
    }

    @Test
    void sphereIgnoresFullHeight() {
        ZoneShape sphere = ZoneShape.sphere(0, 64, 0, 5);
        assertFalse(ZoneGeometry.contains(sphere, 0, 200, 0, MIN_Y, MAX_Y));
    }

    /** The centre is the floor and the height grows upwards — spec section 4. */
    @Test
    void cylinderGrowsUpwardsFromItsCentre() {
        ZoneShape cylinder = ZoneShape.cylinder(0, 64, 0, 10, 20, false);
        assertTrue(ZoneGeometry.contains(cylinder, 0, 64, 0, MIN_Y, MAX_Y), "floor");
        assertTrue(ZoneGeometry.contains(cylinder, 0, 84, 0, MIN_Y, MAX_Y), "ceiling");
        assertFalse(ZoneGeometry.contains(cylinder, 0, 85, 0, MIN_Y, MAX_Y), "above it");
        assertFalse(ZoneGeometry.contains(cylinder, 0, 63, 0, MIN_Y, MAX_Y), "below the floor");
    }

    @Test
    void cylinderIsRoundInXZ() {
        ZoneShape cylinder = ZoneShape.cylinder(0, 64, 0, 10, 20, false);
        assertTrue(ZoneGeometry.contains(cylinder, 10, 70, 0, MIN_Y, MAX_Y));
        assertFalse(ZoneGeometry.contains(cylinder, 8, 70, 8, MIN_Y, MAX_Y), "the corner is outside");
    }

    /** full_height turns the cylinder into "everything within N blocks of this point". */
    @Test
    void cylinderWithFullHeightSpansTheDimension() {
        ZoneShape cylinder = ZoneShape.cylinder(0, 64, 0, 10, 20, true);
        assertTrue(ZoneGeometry.contains(cylinder, 0, MIN_Y, 0, MIN_Y, MAX_Y));
        assertTrue(ZoneGeometry.contains(cylinder, 0, MAX_Y, 0, MIN_Y, MAX_Y));
        assertFalse(ZoneGeometry.contains(cylinder, 20, 70, 0, MIN_Y, MAX_Y), "radius still applies");
    }

    @Test
    void cubeWithFullHeightSpansTheDimension() {
        ZoneShape cube = ZoneShape.cube(0, 60, 0, 10, 70, 10, true);
        assertTrue(ZoneGeometry.contains(cube, 5, MIN_Y, 5, MIN_Y, MAX_Y));
        assertTrue(ZoneGeometry.contains(cube, 5, MAX_Y, 5, MIN_Y, MAX_Y));
        assertFalse(ZoneGeometry.contains(cube, 11, 65, 5, MIN_Y, MAX_Y), "x still applies");
    }

    /** The Nether's ceiling is not the Overworld's. */
    @Test
    void fullHeightUsesTheLimitsItIsGiven() {
        ZoneShape cube = ZoneShape.cube(0, 0, 0, 10, 0, 10, true);
        assertTrue(ZoneGeometry.contains(cube, 5, 127, 5, 0, 128));
        assertFalse(ZoneGeometry.contains(cube, 5, 200, 5, 0, 128));
    }

    @Test
    void aZoneIsTheUnionOfItsShapes() {
        List<ZoneShape> shapes = List.of(
                ZoneShape.cube(0, 0, 0, 10, 10, 10, false),
                ZoneShape.sphere(100, 64, 100, 5));
        assertTrue(ZoneGeometry.containsAny(shapes, 5, 5, 5, MIN_Y, MAX_Y), "in the cube");
        assertTrue(ZoneGeometry.containsAny(shapes, 100, 64, 100, MIN_Y, MAX_Y), "in the sphere");
        assertFalse(ZoneGeometry.containsAny(shapes, 50, 50, 50, MIN_Y, MAX_Y), "in neither");
    }

    /** A zone with no shapes yet — freshly created, not marked out — applies to nobody. */
    @Test
    void aZoneWithoutShapesContainsNothing() {
        assertFalse(ZoneGeometry.containsAny(List.of(), 0, 0, 0, MIN_Y, MAX_Y));
        assertFalse(ZoneGeometry.containsAny(null, 0, 0, 0, MIN_Y, MAX_Y));
    }

    // --- The cheap pre-filter the index leans on ---

    @Test
    void horizontalBoundsEncloseEveryShape() {
        int[] bounds = ZoneGeometry.horizontalBounds(List.of(
                ZoneShape.cube(0, 0, 0, 10, 10, 10, false),
                ZoneShape.sphere(100, 64, 100, 5)));

        assertNotNull(bounds);
        assertEquals(0, bounds[0], "minX");
        assertEquals(105, bounds[1], "maxX");
        assertEquals(0, bounds[2], "minZ");
        assertEquals(105, bounds[3], "maxZ");
    }

    /**
     * A round shape on its own, so nothing masks its four edges.
     *
     * <p>Written after a deliberate off-by-one in the sphere's low edge failed to break anything:
     * every other case here pairs a round shape with a cube whose corner dominates the minimum,
     * so an error on that side was invisible.
     */
    @Test
    void horizontalBoundsOfASphereAloneCoverAllFourEdges() {
        int[] bounds = ZoneGeometry.horizontalBounds(List.of(ZoneShape.sphere(100, 64, 200, 7)));

        assertEquals(93, bounds[0], "minX");
        assertEquals(107, bounds[1], "maxX");
        assertEquals(193, bounds[2], "minZ");
        assertEquals(207, bounds[3], "maxZ");
    }

    @Test
    void horizontalBoundsOfACylinderAloneCoverAllFourEdges() {
        int[] bounds = ZoneGeometry.horizontalBounds(
                List.of(ZoneShape.cylinder(-50, 64, -20, 12, 30, false)));

        assertEquals(-62, bounds[0], "minX");
        assertEquals(-38, bounds[1], "maxX");
        assertEquals(-32, bounds[2], "minZ");
        assertEquals(-8, bounds[3], "maxZ");
    }

    /** Reversed corners must widen the box, not invert it. */
    @Test
    void horizontalBoundsNormaliseReversedCorners() {
        int[] bounds = ZoneGeometry.horizontalBounds(List.of(
                ZoneShape.cube(10, 0, 20, 0, 0, 5, false)));

        assertEquals(0, bounds[0]);
        assertEquals(10, bounds[1]);
        assertEquals(5, bounds[2]);
        assertEquals(20, bounds[3]);
    }

    @Test
    void horizontalBoundsOfNothingAreNull() {
        assertNull(ZoneGeometry.horizontalBounds(List.of()));
        assertNull(ZoneGeometry.horizontalBounds(null));
    }

    // --- Round 2: pushing a player back out ---

    @Test
    void nearestOutsideOfACubeLeavesByTheClosestWall() {
        ZoneShape cube = ZoneShape.cube(0, 0, 0, 10, 10, 10, false);
        // Standing at x=1, the west wall is one block away and every other one is further.
        double[] out = ZoneGeometry.nearestOutside(cube, 1.0, 5.0, 5.0, MIN_Y, MAX_Y);
        assertNotNull(out);
        assertTrue(out[0] < 0.0, "should have been pushed out past x=0, got " + out[0]);
        assertEquals(5.0, out[1], 0.001, "y must not move");
        assertEquals(5.0, out[2], 0.001, "z must not move");
    }

    @Test
    void nearestOutsideOfASphereLeavesAlongTheRadius() {
        ZoneShape sphere = ZoneShape.sphere(0, 64, 0, 10);
        double[] out = ZoneGeometry.nearestOutside(sphere, 3.0, 64.0, 0.0, MIN_Y, MAX_Y);
        assertNotNull(out);
        assertFalse(containsPoint(sphere, out), "the result is still inside");
    }

    /**
     * Dead centre of a sphere there is no nearest wall — every direction is equally far. Pick one
     * and pin it, or the player is pushed somewhere different every tick and vibrates in place.
     */
    @Test
    void nearestOutsideFromTheExactCentreGoesUp() {
        ZoneShape sphere = ZoneShape.sphere(0, 64, 0, 10);
        double[] out = ZoneGeometry.nearestOutside(sphere, 0.0, 64.0, 0.0, MIN_Y, MAX_Y);
        assertEquals(0.0, out[0], 0.001);
        assertEquals(0.0, out[2], 0.001);
        assertTrue(out[1] > 64.0, "should have gone up, got y=" + out[1]);
    }

    @Test
    void nearestOutsideOfACylinderLeavesSideways() {
        ZoneShape cylinder = ZoneShape.cylinder(0, 64, 0, 10, 20, false);
        double[] out = ZoneGeometry.nearestOutside(cylinder, 2.0, 70.0, 0.0, MIN_Y, MAX_Y);
        assertNotNull(out);
        assertFalse(containsPoint(cylinder, out), "the result is still inside");
    }

    /** A shape that spans the whole world vertically has no way out upwards. */
    @Test
    void nearestOutsideOfAFullHeightCylinderStaysHorizontal() {
        ZoneShape cylinder = ZoneShape.cylinder(0, 64, 0, 10, 20, true);
        double[] out = ZoneGeometry.nearestOutside(cylinder, 2.0, 70.0, 0.0, MIN_Y, MAX_Y);
        assertNotNull(out);
        assertEquals(70.0, out[1], 0.001, "y must not move when there is no ceiling to leave by");
        assertFalse(containsPoint(cylinder, out));
    }

    /** A point already outside is left exactly where it is. */
    @Test
    void nearestOutsideOfAPointAlreadyOutsideIsNull() {
        ZoneShape cube = ZoneShape.cube(0, 0, 0, 10, 10, 10, false);
        assertNull(ZoneGeometry.nearestOutside(cube, 50.0, 50.0, 50.0, MIN_Y, MAX_Y));
    }

    /** The inverted case: a cage pushes inwards, and it is the same maths with the sign turned. */
    @Test
    void nearestInsideBringsAPointBackIn() {
        ZoneShape cube = ZoneShape.cube(0, 0, 0, 10, 10, 10, false);
        double[] in = ZoneGeometry.nearestInside(cube, 20.0, 5.0, 5.0, MIN_Y, MAX_Y);
        assertNotNull(in);
        assertTrue(containsPoint(cube, in), "the result is still outside");
    }

    @Test
    void nearestInsideOfAPointAlreadyInsideIsNull() {
        ZoneShape cube = ZoneShape.cube(0, 0, 0, 10, 10, 10, false);
        assertNull(ZoneGeometry.nearestInside(cube, 5.0, 5.0, 5.0, MIN_Y, MAX_Y));
    }

    // --- Round 2: catching a player who moved too fast to be seen inside ---

    /**
     * The whole reason this exists: twenty blocks per tick jumps clean over a thin wall.
     *
     * <p>The slab sits at x=3 rather than x=0 on purpose. At x=0 a coarse walk would land on it by
     * accident — every stride starts there — and the test would pass with a step size far too
     * large to catch anything. Found by breaking the step deliberately and watching nothing fail.
     */
    @Test
    void aSegmentThatSkipsOverAThinZoneIsStillDetected() {
        List<ZoneShape> thin = List.of(ZoneShape.cube(3, 60, 0, 4, 70, 100, false));

        double[] entry = ZoneGeometry.segmentEnters(thin, -20, 65, 50, 20, 65, 50, MIN_Y, MAX_Y);
        assertNotNull(entry, "a segment straight through the slab was not detected");
        assertTrue(entry[0] >= 3.0 && entry[0] < 5.0,
                "entry should be at the near face, got x=" + entry[0]);
    }

    @Test
    void aSegmentThatMissesReturnsNothing() {
        List<ZoneShape> shapes = List.of(ZoneShape.cube(0, 0, 0, 10, 10, 10, false));
        assertNull(ZoneGeometry.segmentEnters(shapes, 50, 5, 5, 60, 5, 5, MIN_Y, MAX_Y));
    }

    /** Starting inside is not "entering"; the per-tick check already owns that case. */
    @Test
    void aSegmentStartingInsideReturnsItsOwnStart() {
        List<ZoneShape> shapes = List.of(ZoneShape.cube(0, 0, 0, 10, 10, 10, false));
        double[] entry = ZoneGeometry.segmentEnters(shapes, 5, 5, 5, 50, 5, 5, MIN_Y, MAX_Y);
        assertNotNull(entry);
        assertEquals(5.0, entry[0], 0.001);
    }

    /** Standing still is the common case and must not cost a walk over the shape list. */
    @Test
    void aSegmentOfZeroLengthOutsideReturnsNothing() {
        List<ZoneShape> shapes = List.of(ZoneShape.cube(0, 0, 0, 10, 10, 10, false));
        assertNull(ZoneGeometry.segmentEnters(shapes, 50, 5, 5, 50, 5, 5, MIN_Y, MAX_Y));
    }

    // --- Round 2: satisfying a whole zone, not just one shape ---

    /** A zone is the union of its shapes, so escaping it means leaving every one of them. */
    @Test
    void escapingAZoneLeavesAllOfItsShapes() {
        List<ZoneShape> touching = List.of(
                ZoneShape.cube(0, 0, 0, 10, 10, 10, false),
                ZoneShape.cube(10, 0, 0, 20, 10, 10, false));

        double[] out = ZoneGeometry.escape(touching, false, 9.0, 5.0, 5.0, MIN_Y, MAX_Y);
        assertNotNull(out);
        assertFalse(ZoneGeometry.containsAny(touching, (int) Math.floor(out[0]),
                (int) Math.floor(out[1]), (int) Math.floor(out[2]), MIN_Y, MAX_Y),
                "pushed out of one shape straight into its neighbour");
    }

    @Test
    void escapingAZoneYouAreNotInIsNull() {
        List<ZoneShape> shapes = List.of(ZoneShape.cube(0, 0, 0, 10, 10, 10, false));
        assertNull(ZoneGeometry.escape(shapes, false, 50.0, 50.0, 50.0, MIN_Y, MAX_Y));
    }

    /** An inverted zone is a cage: satisfying it means getting back inside one of the shapes. */
    @Test
    void escapingAnInvertedZoneBringsYouIn() {
        List<ZoneShape> shapes = List.of(ZoneShape.cube(0, 0, 0, 10, 10, 10, false));

        double[] in = ZoneGeometry.escape(shapes, true, 40.0, 5.0, 5.0, MIN_Y, MAX_Y);
        assertNotNull(in);
        assertTrue(ZoneGeometry.containsAny(shapes, (int) Math.floor(in[0]),
                (int) Math.floor(in[1]), (int) Math.floor(in[2]), MIN_Y, MAX_Y));
    }

    @Test
    void beingInsideAnInvertedZoneNeedsNoEscape() {
        List<ZoneShape> shapes = List.of(ZoneShape.cube(0, 0, 0, 10, 10, 10, false));
        assertNull(ZoneGeometry.escape(shapes, true, 5.0, 5.0, 5.0, MIN_Y, MAX_Y));
    }

    /**
     * Shapes that box a point in completely have no way out.
     *
     * <p>Returning something that is still inside is the honest answer; the caller checks and
     * leaves the player where they are rather than shoving them around forever.
     */
    @Test
    void escapingAnImpossibleArrangementGivesUpRatherThanLooping() {
        List<ZoneShape> everywhere = List.of(
                ZoneShape.cube(-1000, 0, -1000, 1000, 0, 1000, true));
        double[] out = ZoneGeometry.escape(everywhere, false, 0.0, 64.0, 0.0, MIN_Y, MAX_Y);
        // Whatever comes back, the call has to return rather than hang.
        assertNotNull(out);
    }

    /** The block a pushed-to position lands in, which is what the zone is actually measured in. */
    private static boolean containsPoint(ZoneShape shape, double[] p) {
        return ZoneGeometry.contains(shape, (int) Math.floor(p[0]), (int) Math.floor(p[1]),
                (int) Math.floor(p[2]), MIN_Y, MAX_Y);
    }

    /**
     * The pre-filter must never exclude a point the exact test would accept — a box too small
     * silently unlocks part of a zone, which is the one failure nobody would spot in game.
     */
    @Test
    void everyContainedPointIsInsideTheBounds() {
        List<ZoneShape> shapes = List.of(
                ZoneShape.cube(-30, 0, -30, -10, 10, -10, false),
                ZoneShape.cylinder(50, 64, 50, 12, 20, false),
                ZoneShape.sphere(0, 64, 0, 7));
        int[] bounds = ZoneGeometry.horizontalBounds(shapes);

        for (int x = -60; x <= 80; x++) {
            for (int z = -60; z <= 80; z++) {
                if (!ZoneGeometry.containsAny(shapes, x, 64, z, MIN_Y, MAX_Y)) continue;
                assertTrue(x >= bounds[0] && x <= bounds[1] && z >= bounds[2] && z <= bounds[3],
                        "point " + x + "/" + z + " is inside a shape but outside the bounds");
            }
        }
    }
}
