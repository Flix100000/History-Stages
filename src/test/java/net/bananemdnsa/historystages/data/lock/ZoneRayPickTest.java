package net.bananemdnsa.historystages.data.lock;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which shape a click on the map lands on.
 *
 * <p>Pinned here rather than in game because the failure is quiet: grabbing the shape next to the
 * one you aimed at looks like nothing at all until you have edited the wrong one.
 */
class ZoneRayPickTest {

    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;

    /** The flat map's click: straight down from far above, the way the screen asks. */
    private static int drop(List<ZoneShape> shapes, double x, double z) {
        return ZoneRayPick.nearest(shapes, x, 1e6, z, 0, -1, 0, MIN_Y, MAX_Y);
    }

    private static int pick(List<ZoneShape> shapes, double ox, double oy, double oz,
                            double dx, double dy, double dz) {
        return ZoneRayPick.nearest(shapes, ox, oy, oz, dx, dy, dz, MIN_Y, MAX_Y);
    }

    @Test
    void nothingToHitIsNotAHit() {
        assertEquals(-1, drop(List.of(), 0, 0));
        assertEquals(-1, ZoneRayPick.nearest(null, 0, 0, 0, 0, -1, 0, MIN_Y, MAX_Y));
    }

    // ---------------------------------------------------------------------------------
    // Cube
    // ---------------------------------------------------------------------------------

    @Test
    void aStraightDropHitsTheCubeBelowIt() {
        List<ZoneShape> shapes = List.of(ZoneShape.cube(0, 60, 0, 20, 80, 20, false));

        assertEquals(0, drop(shapes, 10, 10));
    }

    @Test
    void aDropBesideTheCubeMissesIt() {
        List<ZoneShape> shapes = List.of(ZoneShape.cube(0, 60, 0, 20, 80, 20, false));

        assertEquals(-1, drop(shapes, 50, 10));
    }

    @Test
    void theLastBlockOfACubeStillCounts() {
        List<ZoneShape> shapes = List.of(ZoneShape.cube(0, 60, 0, 20, 80, 20, false));

        assertEquals(0, drop(shapes, 20.5, 20.5), "the block at 20 is inside the zone");
        assertEquals(-1, drop(shapes, 21.5, 20.5));
    }

    @Test
    void unsortedCornersDescribeTheSameCube() {
        List<ZoneShape> shapes = List.of(ZoneShape.cube(20, 80, 20, 0, 60, 0, false));

        assertEquals(0, drop(shapes, 10, 10));
    }

    @Test
    void aFullHeightCubeIsHitWhateverTheHeightNumbersSay() {
        List<ZoneShape> shapes = List.of(ZoneShape.cube(0, 60, 0, 20, 61, 20, true));

        assertEquals(0, pick(shapes, 10, 300, 10, 0, -1, 0));
    }

    // ---------------------------------------------------------------------------------
    // Sphere
    // ---------------------------------------------------------------------------------

    @Test
    void theSphereIsRoundRatherThanItsBoundingBox() {
        List<ZoneShape> shapes = List.of(ZoneShape.sphere(0, 70, 0, 10));

        assertEquals(0, drop(shapes, 0, 0));
        assertEquals(-1, drop(shapes, 9, 9), "the corner of the box is not inside the sphere");
    }

    @Test
    void aSphereIsMissedFromBesideIt() {
        List<ZoneShape> shapes = List.of(ZoneShape.sphere(0, 70, 0, 10));

        assertEquals(-1, drop(shapes, 40, 0));
    }

    @Test
    void aSlantedRayHitsWhatItPassesThrough() {
        List<ZoneShape> shapes = List.of(ZoneShape.sphere(100, 70, 100, 15));

        assertEquals(0, pick(shapes, 60, 110, 60, 1, -1, 1));
    }

    // ---------------------------------------------------------------------------------
    // Cylinder
    // ---------------------------------------------------------------------------------

    @Test
    void theCylinderIsRoundAndStandsOnItsFloor() {
        List<ZoneShape> shapes = List.of(ZoneShape.cylinder(0, 60, 0, 10, 20, false));

        assertEquals(0, drop(shapes, 5, 0));
        assertEquals(-1, drop(shapes, 9, 9), "the corner of the box is not inside the cylinder");
    }

    @Test
    void aRayEndingBelowTheCylinderNeverReachesIt() {
        List<ZoneShape> shapes = List.of(ZoneShape.cylinder(0, 60, 0, 10, 20, false));

        assertEquals(-1, pick(shapes, 5, 50, 0, 0, -1, 0), "started under the floor, aimed down");
    }

    @Test
    void aCylinderEndsAtItsHeight() {
        List<ZoneShape> shapes = List.of(ZoneShape.cylinder(0, 60, 0, 10, 20, false));

        assertEquals(-1, pick(shapes, 5, 100, 0, 0, 1, 0), "above the lid, aimed up");
    }

    @Test
    void aFullHeightCylinderReachesTheBuildLimit() {
        List<ZoneShape> shapes = List.of(ZoneShape.cylinder(0, 60, 0, 10, 1, true));

        assertEquals(0, pick(shapes, 5, 310, 0, 0, -1, 0));
    }

    @Test
    void aSlantedRayThroughACylinderWallCounts() {
        List<ZoneShape> shapes = List.of(ZoneShape.cylinder(100, 60, 100, 15, 40, false));

        assertEquals(0, pick(shapes, 60, 90, 100, 1, -0.2, 0));
    }

    // ---------------------------------------------------------------------------------
    // Several at once
    // ---------------------------------------------------------------------------------

    @Test
    void theNearerOfTwoOverlappingShapesWins() {
        List<ZoneShape> shapes = List.of(
                ZoneShape.cube(0, 60, 0, 20, 70, 20, false),
                ZoneShape.cube(0, 100, 0, 20, 110, 20, false));

        assertEquals(1, drop(shapes, 10, 10), "the upper one is nearer to the eye");
    }

    @Test
    void theNearerOneWinsWhicheverOrderTheyAreListedIn() {
        List<ZoneShape> shapes = List.of(
                ZoneShape.cube(0, 100, 0, 20, 110, 20, false),
                ZoneShape.cube(0, 60, 0, 20, 70, 20, false));

        assertEquals(0, drop(shapes, 10, 10));
    }

    @Test
    void aRayStartingInsideAShapeHitsIt() {
        List<ZoneShape> shapes = List.of(ZoneShape.cube(0, 60, 0, 20, 80, 20, false));

        assertEquals(0, pick(shapes, 10, 70, 10, 0, -1, 0));
    }

    @Test
    void theShapeYouAreStandingInBeatsTheOneAboveYou() {
        List<ZoneShape> shapes = List.of(
                ZoneShape.cube(0, 200, 0, 20, 210, 20, false),
                ZoneShape.cube(0, 60, 0, 20, 80, 20, false));

        assertEquals(1, pick(shapes, 10, 70, 10, 0, 1, 0), "aimed up from inside the lower one");
    }
}
