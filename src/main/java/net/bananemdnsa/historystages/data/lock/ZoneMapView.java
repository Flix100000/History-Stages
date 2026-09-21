package net.bananemdnsa.historystages.data.lock;

import java.util.List;

/**
 * Where a point of the world lands on the map, seen from above.
 *
 * <p>Free of Minecraft, and for the same reason as {@link ZoneRowText}: a camera that is two pixels
 * out looks like a rounding artefact and never gets reported. The fit used to live in its own class
 * next to this one; it is a special case of a camera, and two classes that both turn world into
 * pixels drift apart.
 *
 * <p>X to the right, Z downwards. Y does not appear at all — which is why a cube and a cylinder
 * with the same footprint come out the same here, and why {@code full_height} changes nothing.
 */
public record ZoneMapView(double centreX, double centreZ, double blocksPerPixel,
                          int width, int height) {

    /** One block across eight pixels. Closer buys nothing: the map knows no half blocks. */
    public static final double MIN_BLOCKS_PER_PIXEL = 0.125;

    /** 32 blocks to a pixel — a full screen is then about twenty thousand blocks wide. */
    public static final double MAX_BLOCKS_PER_PIXEL = 32.0;

    /** Share of the box left free per side by the fit, so a shape never touches the frame. */
    private static final double PADDING = 0.08;

    /** A world extent below this is widened to it, so a one-block zone still gets a scale. */
    private static final double MIN_EXTENT = 1.0;

    /** Grid steps that stay on chunk boundaries. */
    private static final int[] GRID_STEPS = {16, 32, 64, 128, 256, 512, 1024, 2048, 4096};

    /** The scale bar may be odd, as long as it reads well. */
    private static final int[] SCALE_STEPS =
            {1, 2, 5, 10, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096};

    /** How far apart two grid lines have to stay before the step is coarsened. */
    private static final int MIN_GRID_GAP = 4;

    /** A shape's box in the map's own pixels. {@code round} means circle rather than rectangle. */
    public record Rect(float x, float y, float w, float h, boolean round) {}

    /** {@code minX, minZ, maxX, maxZ} of one shape seen from above. */
    public record Footprint(double minX, double minZ, double maxX, double maxZ) {}

    public float screenX(double worldX) {
        return (float) (width / 2.0 + (worldX - centreX) / blocksPerPixel);
    }

    public float screenZ(double worldZ) {
        return (float) (height / 2.0 + (worldZ - centreZ) / blocksPerPixel);
    }

    public double worldX(double screenX) {
        return centreX + (screenX - width / 2.0) * blocksPerPixel;
    }

    public double worldZ(double screenZ) {
        return centreZ + (screenZ - height / 2.0) * blocksPerPixel;
    }

    /**
     * The corners of a cube are sorted here rather than trusted: the editor lets either corner be
     * given first, and an unsorted subtraction hands back a negative width.
     */
    public static Footprint footprint(ZoneShape shape) {
        return switch (shape.type()) {
            case CUBE -> new Footprint(
                    Math.min(shape.fromX(), shape.toX()), Math.min(shape.fromZ(), shape.toZ()),
                    Math.max(shape.fromX(), shape.toX()), Math.max(shape.fromZ(), shape.toZ()));
            case SPHERE, CYLINDER -> new Footprint(
                    shape.fromX() - shape.radius(), shape.fromZ() - shape.radius(),
                    shape.fromX() + shape.radius(), shape.fromZ() + shape.radius());
        };
    }

    /**
     * The view that shows every shape.
     *
     * <p>The aspect ratio is kept. Stretching to fill the box would make a long thin zone look
     * square, which is a lie about the very thing being edited.
     */
    public static ZoneMapView fitting(List<ZoneShape> shapes, int width, int height) {
        int w = Math.max(1, width);
        int h = Math.max(1, height);
        if (shapes == null || shapes.isEmpty()) return new ZoneMapView(0, 0, 1.0, w, h);

        double minX = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (ZoneShape shape : shapes) {
            Footprint f = footprint(shape);
            minX = Math.min(minX, f.minX());
            minZ = Math.min(minZ, f.minZ());
            maxX = Math.max(maxX, f.maxX());
            maxZ = Math.max(maxZ, f.maxZ());
        }

        double spanX = Math.max(MIN_EXTENT, maxX - minX);
        double spanZ = Math.max(MIN_EXTENT, maxZ - minZ);
        double bpp = Math.max(spanX / (w * (1 - 2 * PADDING)), spanZ / (h * (1 - 2 * PADDING)));

        return new ZoneMapView((minX + maxX) / 2, (minZ + maxZ) / 2, clampZoom(bpp), w, h);
    }

    private static double clampZoom(double bpp) {
        return Math.max(MIN_BLOCKS_PER_PIXEL, Math.min(MAX_BLOCKS_PER_PIXEL, bpp));
    }

    public ZoneMapView zoomed(double factor) {
        return new ZoneMapView(centreX, centreZ, clampZoom(blocksPerPixel * factor), width, height);
    }

    /** Dragging: the point under the cursor should travel with it, so the world moves the other way. */
    public ZoneMapView movedByPixels(double dx, double dz) {
        return new ZoneMapView(centreX - dx * blocksPerPixel, centreZ - dz * blocksPerPixel,
                blocksPerPixel, width, height);
    }

    public ZoneMapView centredOn(double x, double z) {
        return new ZoneMapView(x, z, blocksPerPixel, width, height);
    }

    public ZoneMapView resized(int newWidth, int newHeight) {
        return new ZoneMapView(centreX, centreZ, blocksPerPixel,
                Math.max(1, newWidth), Math.max(1, newHeight));
    }

    public Rect rectFor(ZoneShape shape) {
        Footprint f = footprint(shape);
        float x = screenX(f.minX());
        float y = screenZ(f.minZ());
        return new Rect(x, y, screenX(f.maxX()) - x, screenZ(f.maxZ()) - y,
                shape.type() != ZoneShapeType.CUBE);
    }

    public double worldMinX() {
        return worldX(0);
    }

    public double worldMaxX() {
        return worldX(width);
    }

    public double worldMinZ() {
        return worldZ(0);
    }

    public double worldMaxZ() {
        return worldZ(height);
    }

    /** The finest grid step whose lines still stay {@link #MIN_GRID_GAP} pixels apart. */
    public int minorGridStep() {
        for (int step : GRID_STEPS) {
            if (step / blocksPerPixel >= MIN_GRID_GAP) return step;
        }
        return GRID_STEPS[GRID_STEPS.length - 1];
    }

    /**
     * The brighter line, the one that carries a coordinate.
     *
     * <p>Always eight times the fine step so the two nest — at the default zoom that is the
     * familiar 16 and 128.
     */
    public int majorGridStep() {
        return minorGridStep() * 8;
    }

    /** The largest round block count that still fits into {@code maxPixels}. */
    public int scaleBarBlocks(int maxPixels) {
        int best = SCALE_STEPS[0];
        for (int step : SCALE_STEPS) {
            if (step / blocksPerPixel <= maxPixels) best = step;
        }
        return best;
    }
}
