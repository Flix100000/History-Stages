package net.bananemdnsa.historystages.data.lock;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tilted camera.
 *
 * <p>The reason these exist: a projection that is subtly wrong does not crash and does not look
 * broken — it looks like a drawing bug, and the search starts in the wrong file. The property worth
 * guarding above all others is that straight down reproduces the flat map, because that is what
 * makes switching between the two views readable.
 */
class ZoneOrbitCameraTest {

    private static final int W = 200;
    private static final int H = 100;

    private static ZoneOrbitCamera topDown(double bpp) {
        return new ZoneOrbitCamera(0, 64, 0, 0, 90, bpp, W, H);
    }

    // ---------------------------------------------------------------------------------
    // Meeting the flat map at the limit
    // ---------------------------------------------------------------------------------

    @Test
    void straightDownPutsTheCentreInTheMiddle() {
        ZoneOrbitCamera.Projected p = topDown(1.0).project(0, 64, 0);

        assertEquals(W / 2f, p.x(), 0.001f);
        assertEquals(H / 2f, p.y(), 0.001f);
    }

    @Test
    void straightDownAgreesWithTheFlatMap() {
        ZoneMapView flat = new ZoneMapView(120, -40, 2.0, W, H);
        ZoneOrbitCamera tipped = new ZoneOrbitCamera(120, 64, -40, 0, 90, 2.0, W, H);

        for (double[] point : new double[][] {{120, -40}, {200, 30}, {-15, -300}, {0, 0}}) {
            ZoneOrbitCamera.Projected p = tipped.project(point[0], 64, point[1]);
            assertEquals(flat.screenX(point[0]), p.x(), 0.01f,
                    "X drifts apart at " + point[0] + "/" + point[1]);
            assertEquals(flat.screenZ(point[1]), p.y(), 0.01f,
                    "Z drifts apart at " + point[0] + "/" + point[1]);
        }
    }

    @Test
    void eastIsRightAndSouthIsDownWhenLookingStraightDown() {
        ZoneOrbitCamera camera = topDown(1.0);

        assertTrue(camera.project(50, 64, 0).x() > W / 2f, "+X should be to the right");
        assertTrue(camera.project(0, 64, 50).y() > H / 2f, "+Z should be downwards");
    }

    // ---------------------------------------------------------------------------------
    // Tilting
    // ---------------------------------------------------------------------------------

    @Test
    void heightGoesUpTheScreenWhateverTheTilt() {
        for (double pitch : new double[] {15, 35, 55, 80, 89}) {
            ZoneOrbitCamera camera = new ZoneOrbitCamera(0, 64, 0, 30, pitch, 1.0, W, H);
            assertTrue(camera.project(0, 100, 0).y() < camera.project(0, 64, 0).y(),
                    "higher did not draw higher at pitch " + pitch);
        }
    }

    @Test
    void heightIsInvisibleOnlyWhenLookingStraightDown() {
        ZoneOrbitCamera flatOn = new ZoneOrbitCamera(0, 64, 0, 0, 90, 1.0, W, H);
        assertEquals(flatOn.project(0, 64, 0).y(), flatOn.project(0, 200, 0).y(), 0.01f);

        ZoneOrbitCamera tilted = new ZoneOrbitCamera(0, 64, 0, 0, 55, 1.0, W, H);
        assertTrue(Math.abs(tilted.project(0, 200, 0).y() - tilted.project(0, 64, 0).y()) > 10,
                "a hundred blocks of height have to show once the view is tipped");
    }

    @Test
    void thePictureNeverRolls() {
        for (double yaw : new double[] {0, 47, 123, 250, 359}) {
            ZoneOrbitCamera camera = new ZoneOrbitCamera(0, 64, 0, yaw, 40, 1.0, W, H);

            assertEquals(0.0, camera.right().y(), 1e-9,
                    "screen right tilted out of the horizon at " + yaw);
        }
    }

    @Test
    void theTiltStopsAtItsLimits() {
        ZoneOrbitCamera camera = new ZoneOrbitCamera(0, 64, 0, 0, 55, 1.0, W, H);

        assertEquals(ZoneOrbitCamera.MAX_PITCH, camera.turnedBy(0, 500).pitchDegrees(), 0.001);
        assertEquals(ZoneOrbitCamera.MIN_PITCH, camera.turnedBy(0, -500).pitchDegrees(), 0.001);
    }

    @Test
    void turningAllTheWayRoundComesBackToTheStart() {
        ZoneOrbitCamera camera = new ZoneOrbitCamera(0, 64, 0, 30, 55, 1.0, W, H);
        ZoneOrbitCamera.Projected before = camera.project(100, 70, 100);
        ZoneOrbitCamera.Projected after = camera.turnedBy(360, 0).project(100, 70, 100);

        assertEquals(before.x(), after.x(), 0.01f);
        assertEquals(before.y(), after.y(), 0.01f);
    }

    @Test
    void yawStaysInsideAFullTurn() {
        ZoneOrbitCamera camera = new ZoneOrbitCamera(0, 64, 0, 350, 55, 1.0, W, H);

        assertEquals(20.0, camera.turnedBy(30, 0).yawDegrees(), 0.001);
        assertEquals(340.0, camera.turnedBy(-10, 0).yawDegrees(), 0.001);
    }

    // ---------------------------------------------------------------------------------
    // Depth and zoom
    // ---------------------------------------------------------------------------------

    @Test
    void whatIsFurtherFromTheEyeHasTheGreaterDepth() {
        ZoneOrbitCamera camera = new ZoneOrbitCamera(0, 64, 0, 0, 45, 1.0, W, H);
        ZoneOrbitCamera.Vec forward = camera.forward();

        double near = camera.project(-forward.x() * 50, 64 - forward.y() * 50,
                -forward.z() * 50).depth();
        double far = camera.project(forward.x() * 50, 64 + forward.y() * 50,
                forward.z() * 50).depth();

        assertTrue(far > near, "sorting back to front would come out reversed");
    }

    @Test
    void theZoomStopsAtItsLimits() {
        ZoneOrbitCamera camera = new ZoneOrbitCamera(0, 64, 0, 0, 55, 1.0, W, H);
        for (int i = 0; i < 40; i++) camera = camera.zoomed(2);
        assertEquals(ZoneOrbitCamera.MAX_BLOCKS_PER_PIXEL, camera.blocksPerPixel(), 0.0001);

        for (int i = 0; i < 80; i++) camera = camera.zoomed(0.5);
        assertEquals(ZoneOrbitCamera.MIN_BLOCKS_PER_PIXEL, camera.blocksPerPixel(), 0.0001);
    }

    @Test
    void takingOverTheFlatViewKeepsCentreAndScale() {
        ZoneMapView flat = new ZoneMapView(300, -12, 3.5, W, H);
        ZoneOrbitCamera camera = ZoneOrbitCamera.from(flat, 70);

        assertEquals(300.0, camera.centreX(), 0.001);
        assertEquals(-12.0, camera.centreZ(), 0.001);
        assertEquals(3.5, camera.blocksPerPixel(), 0.0001);
    }

    // ---------------------------------------------------------------------------------
    // Clicking
    // ---------------------------------------------------------------------------------

    @Test
    void aClickTracesBackToWhatWasDrawnThere() {
        ZoneOrbitCamera camera = new ZoneOrbitCamera(0, 64, 0, 30, 55, 0.5, W, H);
        ZoneOrbitCamera.Projected drawn = camera.project(40, 80, -25);

        ZoneOrbitCamera.Vec origin = camera.rayOrigin(drawn.x(), drawn.y());
        ZoneOrbitCamera.Vec forward = camera.forward();

        // The point has to sit on the line, so both ways of measuring it must agree.
        double t = (40 - origin.x()) / forward.x();
        assertEquals(80, origin.y() + forward.y() * t, 0.05);
        assertEquals(-25, origin.z() + forward.z() * t, 0.05);
    }

    @Test
    void clickingAShapeFindsIt() {
        ZoneOrbitCamera camera = new ZoneOrbitCamera(0, 64, 0, 30, 55, 0.5, W, H);
        List<ZoneShape> shapes = List.of(ZoneShape.cube(-5, 60, -5, 5, 70, 5, false));
        ZoneOrbitCamera.Projected middle = camera.project(0, 65, 0);

        assertEquals(0, pickAt(camera, shapes, middle.x(), middle.y()));
    }

    @Test
    void clickingBesideEverythingFindsNothing() {
        ZoneOrbitCamera camera = new ZoneOrbitCamera(0, 64, 0, 30, 55, 0.5, W, H);
        List<ZoneShape> shapes = List.of(ZoneShape.cube(-5, 60, -5, 5, 70, 5, false));
        ZoneOrbitCamera.Projected away = camera.project(400, 65, 400);

        assertEquals(-1, pickAt(camera, shapes, away.x(), away.y()));
    }

    @Test
    void theNearerShapeIsPickedFromWhicheverSideTheCameraLooks() {
        List<ZoneShape> shapes = List.of(
                ZoneShape.cube(-60, 60, -5, -50, 70, 5, false),
                ZoneShape.cube(50, 60, -5, 60, 70, 5, false));

        // Yaw 90 looks east, so the eastern box is the near one; yaw 270 turns that around.
        assertEquals(1, pickAt(new ZoneOrbitCamera(55, 65, 0, 90, 20, 0.5, W, H),
                shapes, W / 2.0, H / 2.0));
        assertEquals(0, pickAt(new ZoneOrbitCamera(-55, 65, 0, 270, 20, 0.5, W, H),
                shapes, W / 2.0, H / 2.0));
    }

    private static int pickAt(ZoneOrbitCamera camera, List<ZoneShape> shapes,
                              double screenX, double screenY) {
        ZoneOrbitCamera.Vec origin = camera.rayOrigin(screenX, screenY);
        ZoneOrbitCamera.Vec forward = camera.forward();
        return ZoneRayPick.nearest(shapes, origin.x(), origin.y(), origin.z(),
                forward.x(), forward.y(), forward.z(), -64, 320);
    }
}
