package net.bananemdnsa.historystages.data.lock;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The camera of the zone map.
 *
 * <p>Worth pinning without the game running: this is the half of the map that can be wrong
 * invisibly. A shape half outside its box reads as a rounding artefact rather than as a bug in the
 * fit, so nobody would report it.
 */
class ZoneMapViewTest {

    private static final int BOX = 100;

    // ---------------------------------------------------------------------------------
    // Turning world into pixels
    // ---------------------------------------------------------------------------------

    @Test
    void theCentreOfTheViewLandsInTheMiddleOfTheBox() {
        ZoneMapView view = new ZoneMapView(200, 300, 1.0, BOX, BOX);

        assertEquals(50f, view.screenX(200), 0.001f);
        assertEquals(50f, view.screenZ(300), 0.001f);
    }

    @Test
    void screenAndWorldAreInverses() {
        ZoneMapView view = new ZoneMapView(-40.5, 1024, 0.25, 160, 90);

        assertEquals(73.5, view.worldX(view.screenX(73.5)), 0.001);
        assertEquals(-11.25, view.worldZ(view.screenZ(-11.25)), 0.001);
    }

    @Test
    void aPixelIsBlocksPerPixelWide() {
        ZoneMapView view = new ZoneMapView(0, 0, 4.0, BOX, BOX);

        assertEquals(1f, view.screenX(4) - view.screenX(0), 0.001f);
    }

    // ---------------------------------------------------------------------------------
    // The fit — these came over from ZonePreviewLayoutTest
    // ---------------------------------------------------------------------------------

    @Test
    void withoutShapesTheViewSitsAtTheOriginRatherThanCrashing() {
        ZoneMapView view = ZoneMapView.fitting(List.of(), BOX, BOX);

        assertEquals(0.0, view.centreX(), 0.001);
        assertEquals(0.0, view.centreZ(), 0.001);
    }

    @Test
    void nullIsTreatedLikeAnEmptyList() {
        assertEquals(0.0, ZoneMapView.fitting(null, BOX, BOX).centreX(), 0.001);
    }

    @Test
    void aBoxOfNoSizeStillGivesAUsableView() {
        ZoneMapView view = ZoneMapView.fitting(
                List.of(ZoneShape.cube(0, 0, 0, 10, 0, 10, false)), 0, 0);

        assertTrue(view.width() > 0 && view.height() > 0);
        assertTrue(view.blocksPerPixel() > 0);
    }

    @Test
    void oneCubeSitsInsideTheBox() {
        ZoneShape cube = ZoneShape.cube(0, 0, 0, 100, 0, 100, false);
        ZoneMapView view = ZoneMapView.fitting(List.of(cube), BOX, BOX);
        ZoneMapView.Rect r = view.rectFor(cube);

        assertTrue(r.x() >= 0 && r.y() >= 0, "left/top outside: " + r);
        assertTrue(r.x() + r.w() <= BOX, "right outside: " + r);
        assertTrue(r.y() + r.h() <= BOX, "bottom outside: " + r);
    }

    @Test
    void severalShapesAllSitInsideTheBox() {
        List<ZoneShape> shapes = List.of(
                ZoneShape.cube(-200, 0, -200, -100, 0, -100, false),
                ZoneShape.sphere(300, 70, 300, 40),
                ZoneShape.cylinder(0, 60, 500, 25, 30, false));
        ZoneMapView view = ZoneMapView.fitting(shapes, BOX, BOX);

        for (ZoneShape shape : shapes) {
            ZoneMapView.Rect r = view.rectFor(shape);
            assertTrue(r.x() >= -0.001f && r.y() >= -0.001f, "left/top outside: " + r);
            assertTrue(r.x() + r.w() <= BOX + 0.001f, "right outside: " + r);
            assertTrue(r.y() + r.h() <= BOX + 0.001f, "bottom outside: " + r);
        }
    }

    @Test
    void aLongThinZoneKeepsItsProportions() {
        ZoneShape cube = ZoneShape.cube(0, 0, 0, 400, 0, 100, false);
        ZoneMapView view = ZoneMapView.fitting(List.of(cube), BOX, BOX);
        ZoneMapView.Rect r = view.rectFor(cube);

        assertEquals(4.0, r.w() / r.h(), 0.05, "stretched rather than to scale: " + r);
    }

    @Test
    void unsortedCubeCornersStillGiveAPositiveWidth() {
        ZoneShape backwards = ZoneShape.cube(160, 0, 120, 100, 0, 60, false);
        ZoneMapView view = ZoneMapView.fitting(List.of(backwards), BOX, BOX);
        ZoneMapView.Rect r = view.rectFor(backwards);

        assertTrue(r.w() > 0, "negative width: " + r);
        assertTrue(r.h() > 0, "negative height: " + r);
    }

    @Test
    void aSphereIsSquareAndMarkedRound() {
        ZoneShape sphere = ZoneShape.sphere(0, 70, 0, 30);
        ZoneMapView view = ZoneMapView.fitting(List.of(sphere), BOX, BOX);
        ZoneMapView.Rect r = view.rectFor(sphere);

        assertEquals(r.w(), r.h(), 0.001f);
        assertTrue(r.round());
    }

    @Test
    void aCubeIsNotMarkedRound() {
        ZoneShape cube = ZoneShape.cube(0, 0, 0, 10, 0, 10, false);

        assertTrue(!ZoneMapView.fitting(List.of(cube), BOX, BOX).rectFor(cube).round());
    }

    @Test
    void aCylinderIsAsWideAsItIsDeep() {
        ZoneShape cylinder = ZoneShape.cylinder(50, 60, -20, 16, 30, true);
        ZoneMapView view = ZoneMapView.fitting(List.of(cylinder), BOX, BOX);
        ZoneMapView.Rect r = view.rectFor(cylinder);

        assertEquals(r.w(), r.h(), 0.001f);
        assertTrue(r.round());
    }

    @Test
    void fullHeightMakesNoDifferenceSeenFromAbove() {
        ZoneShape flat = ZoneShape.cube(0, 60, 0, 40, 70, 40, false);
        ZoneShape tall = ZoneShape.cube(0, 60, 0, 40, 70, 40, true);
        ZoneMapView view = ZoneMapView.fitting(List.of(flat), BOX, BOX);

        assertEquals(view.rectFor(flat), view.rectFor(tall));
    }

    @Test
    void aOneBlockZoneStillGetsAUsableScale() {
        ZoneShape tiny = ZoneShape.cube(10, 0, 10, 10, 0, 10, false);
        ZoneMapView view = ZoneMapView.fitting(List.of(tiny), BOX, BOX);

        assertTrue(view.blocksPerPixel() > 0, "a scale of zero makes every conversion infinite");
    }

    // ---------------------------------------------------------------------------------
    // Zooming and dragging
    // ---------------------------------------------------------------------------------

    @Test
    void zoomingInAndOutAgainReturnsToTheSameScale() {
        ZoneMapView view = new ZoneMapView(0, 0, 1.0, BOX, BOX);

        assertEquals(1.0, view.zoomed(2).zoomed(0.5).blocksPerPixel(), 0.0001);
    }

    @Test
    void theZoomStopsAtItsLimits() {
        ZoneMapView view = new ZoneMapView(0, 0, 1.0, BOX, BOX);
        for (int i = 0; i < 40; i++) view = view.zoomed(2);
        assertEquals(ZoneMapView.MAX_BLOCKS_PER_PIXEL, view.blocksPerPixel(), 0.0001);

        for (int i = 0; i < 80; i++) view = view.zoomed(0.5);
        assertEquals(ZoneMapView.MIN_BLOCKS_PER_PIXEL, view.blocksPerPixel(), 0.0001);
    }

    @Test
    void theFitIsClampedToTheSameLimits() {
        ZoneShape huge = ZoneShape.cube(-2_000_000, 0, -2_000_000, 2_000_000, 0, 2_000_000, false);
        ZoneMapView view = ZoneMapView.fitting(List.of(huge), BOX, BOX);

        assertEquals(ZoneMapView.MAX_BLOCKS_PER_PIXEL, view.blocksPerPixel(), 0.0001);
    }

    @Test
    void draggingMovesTheCentreAgainstThePointer() {
        ZoneMapView view = new ZoneMapView(100, 100, 2.0, BOX, BOX).movedByPixels(10, -5);

        assertEquals(80.0, view.centreX(), 0.001);
        assertEquals(110.0, view.centreZ(), 0.001);
    }

    @Test
    void draggingLeavesTheScaleAlone() {
        ZoneMapView view = new ZoneMapView(0, 0, 3.0, BOX, BOX).movedByPixels(40, 40);

        assertEquals(3.0, view.blocksPerPixel(), 0.0001);
    }

    @Test
    void resizingKeepsTheCentreAndTheScale() {
        ZoneMapView view = new ZoneMapView(12, -34, 2.5, BOX, BOX).resized(300, 200);

        assertEquals(12.0, view.centreX(), 0.001);
        assertEquals(-34.0, view.centreZ(), 0.001);
        assertEquals(2.5, view.blocksPerPixel(), 0.0001);
        assertEquals(300, view.width());
    }

    @Test
    void theVisibleWorldSpansTheWholeBox() {
        ZoneMapView view = new ZoneMapView(0, 0, 2.0, 100, 50);

        assertEquals(-100.0, view.worldMinX(), 0.001);
        assertEquals(100.0, view.worldMaxX(), 0.001);
        assertEquals(-50.0, view.worldMinZ(), 0.001);
        assertEquals(50.0, view.worldMaxZ(), 0.001);
    }

    // ---------------------------------------------------------------------------------
    // Grid and scale bar
    // ---------------------------------------------------------------------------------

    @Test
    void theGridThinsOutInsteadOfTurningGrey() {
        assertEquals(16, new ZoneMapView(0, 0, 1.0, BOX, BOX).minorGridStep());
        assertEquals(16, new ZoneMapView(0, 0, 4.0, BOX, BOX).minorGridStep());
        assertEquals(32, new ZoneMapView(0, 0, 8.0, BOX, BOX).minorGridStep());
        assertEquals(64, new ZoneMapView(0, 0, 16.0, BOX, BOX).minorGridStep());
    }

    @Test
    void everyGridStepKeepsItsLinesApart() {
        for (double bpp : new double[] {0.125, 0.5, 1, 3, 4.1, 7, 12, 25, 32}) {
            ZoneMapView view = new ZoneMapView(0, 0, bpp, BOX, BOX);
            assertTrue(view.minorGridStep() / bpp >= 4,
                    "lines closer than four pixels at " + bpp);
        }
    }

    @Test
    void theMajorGridIsAlwaysAMultipleOfTheMinorOne() {
        for (double bpp : new double[] {0.25, 1, 3, 7, 12, 25}) {
            ZoneMapView view = new ZoneMapView(0, 0, bpp, BOX, BOX);
            assertEquals(0, view.majorGridStep() % view.minorGridStep(),
                    "grids do not nest at " + bpp);
        }
    }

    @Test
    void theScaleBarPicksTheLargestRoundNumberThatStillFits() {
        assertEquals(64, new ZoneMapView(0, 0, 1.0, BOX, BOX).scaleBarBlocks(90));
        assertEquals(512, new ZoneMapView(0, 0, 8.0, BOX, BOX).scaleBarBlocks(90));
    }

    @Test
    void theScaleBarFallsBackToOneBlockWhenNothingFits() {
        assertEquals(1, new ZoneMapView(0, 0, 32.0, BOX, BOX).scaleBarBlocks(0));
    }

    @Test
    void theScaleBarNeverClaimsMoreRoomThanItWasGiven() {
        for (double bpp : new double[] {0.125, 0.5, 1, 2.7, 9, 32}) {
            ZoneMapView view = new ZoneMapView(0, 0, bpp, BOX, BOX);
            int blocks = view.scaleBarBlocks(90);
            assertTrue(blocks == 1 || blocks / bpp <= 90,
                    "bar wider than 90 px at " + bpp + ": " + blocks);
        }
    }
}
