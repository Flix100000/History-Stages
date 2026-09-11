package net.bananemdnsa.historystages.data.lock;

/**
 * The tilted view: an orbit around a point in the world, projected flat.
 *
 * <p>Orthographic rather than perspective, and deliberately. A perspective picture makes the far
 * side of a zone smaller than the near side, which turns every judgement about size into a guess;
 * with parallel rays a hundred blocks are a hundred blocks wherever they sit. It also keeps a
 * sphere drawn as a circle instead of smearing it into an ellipse near the edge of the screen.
 *
 * <p>The angles are chosen so that straight down reproduces {@link ZoneMapView} exactly: at a pitch
 * of ninety degrees and a yaw of zero, X runs right and Z runs down, the same as on the flat map.
 * The two views therefore meet at the limit instead of disagreeing there, which is what makes the
 * switch between them readable.
 *
 * <p>Free of Minecraft, so the projection and the ray behind a click can be pinned by tests. A
 * camera that is subtly wrong looks like a drawing bug and gets chased in the wrong file.
 */
public record ZoneOrbitCamera(double centreX, double centreY, double centreZ,
                              double yawDegrees, double pitchDegrees, double blocksPerPixel,
                              int width, int height) {

    /** Flat on is useless — the world turns into a line — and past vertical the picture flips. */
    public static final double MIN_PITCH = 15.0;
    public static final double MAX_PITCH = 89.0;

    public static final double MIN_BLOCKS_PER_PIXEL = ZoneMapView.MIN_BLOCKS_PER_PIXEL;
    public static final double MAX_BLOCKS_PER_PIXEL = ZoneMapView.MAX_BLOCKS_PER_PIXEL;

    /** How far back the eye is put before a click is traced, so everything in sight lies ahead of it. */
    private static final double EYE_SETBACK = 100_000.0;

    /** A direction in the world. */
    public record Vec(double x, double y, double z) {}

    /** Where a world point lands, plus how far along the line of sight it sits. */
    public record Projected(float x, float y, double depth) {}

    public static ZoneOrbitCamera looking(double centreX, double centreY, double centreZ,
                                          double blocksPerPixel, int width, int height) {
        return new ZoneOrbitCamera(centreX, centreY, centreZ, 30, 55,
                clampZoom(blocksPerPixel), Math.max(1, width), Math.max(1, height));
    }

    /** The flat map's view, tipped over — same centre, same scale, so switching does not jump. */
    public static ZoneOrbitCamera from(ZoneMapView view, double centreY) {
        return looking(view.centreX(), centreY, view.centreZ(),
                view.blocksPerPixel(), view.width(), view.height());
    }

    /**
     * Where the camera looks, pointing into the picture.
     *
     * <p>Yaw zero looks north, so that tipping all the way down lands on the flat map's north-up
     * orientation rather than upside down.
     */
    public Vec forward() {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        return new Vec(Math.sin(yaw) * Math.cos(pitch),
                -Math.sin(pitch),
                -Math.cos(yaw) * Math.cos(pitch));
    }

    /** Screen right. Level with the horizon whatever the tilt, so the picture never rolls. */
    public Vec right() {
        double yaw = Math.toRadians(yawDegrees);
        return new Vec(Math.cos(yaw), 0, Math.sin(yaw));
    }

    /** Screen up. */
    public Vec up() {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        return new Vec(Math.sin(yaw) * Math.sin(pitch),
                Math.cos(pitch),
                -Math.cos(yaw) * Math.sin(pitch));
    }

    public Projected project(double x, double y, double z) {
        double dx = x - centreX;
        double dy = y - centreY;
        double dz = z - centreZ;

        Vec right = right();
        Vec up = up();
        Vec forward = forward();

        double across = dx * right.x() + dy * right.y() + dz * right.z();
        double along = dx * up.x() + dy * up.y() + dz * up.z();
        double depth = dx * forward.x() + dy * forward.y() + dz * forward.z();

        return new Projected((float) (width / 2.0 + across / blocksPerPixel),
                (float) (height / 2.0 - along / blocksPerPixel), depth);
    }

    /**
     * Where a click starts in the world.
     *
     * <p>The eye is pushed a long way back along the line of sight so that everything in the
     * picture lies in front of it. Started on the plane through the centre instead, anything nearer
     * than that plane would come back with a negative distance and win the pick while standing
     * behind the thing the author was pointing at.
     */
    public Vec rayOrigin(double screenX, double screenY) {
        Vec right = right();
        Vec up = up();
        Vec forward = forward();

        double across = (screenX - width / 2.0) * blocksPerPixel;
        double along = -(screenY - height / 2.0) * blocksPerPixel;

        return new Vec(
                centreX + right.x() * across + up.x() * along - forward.x() * EYE_SETBACK,
                centreY + right.y() * across + up.y() * along - forward.y() * EYE_SETBACK,
                centreZ + right.z() * across + up.z() * along - forward.z() * EYE_SETBACK);
    }

    public ZoneOrbitCamera turnedBy(double dYaw, double dPitch) {
        double pitch = Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitchDegrees + dPitch));
        return new ZoneOrbitCamera(centreX, centreY, centreZ,
                wrap(yawDegrees + dYaw), pitch, blocksPerPixel, width, height);
    }

    public ZoneOrbitCamera zoomed(double factor) {
        return new ZoneOrbitCamera(centreX, centreY, centreZ, yawDegrees, pitchDegrees,
                clampZoom(blocksPerPixel * factor), width, height);
    }

    public ZoneOrbitCamera centredOn(double x, double y, double z) {
        return new ZoneOrbitCamera(x, y, z, yawDegrees, pitchDegrees, blocksPerPixel, width, height);
    }

    public ZoneOrbitCamera resized(int newWidth, int newHeight) {
        return new ZoneOrbitCamera(centreX, centreY, centreZ, yawDegrees, pitchDegrees,
                blocksPerPixel, Math.max(1, newWidth), Math.max(1, newHeight));
    }

    private static double clampZoom(double bpp) {
        return Math.max(MIN_BLOCKS_PER_PIXEL, Math.min(MAX_BLOCKS_PER_PIXEL, bpp));
    }

    private static double wrap(double degrees) {
        double d = degrees % 360;
        return d < 0 ? d + 360 : d;
    }
}
