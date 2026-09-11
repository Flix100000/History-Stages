package net.bananemdnsa.historystages.client.editor.zone;

import java.util.Arrays;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.bananemdnsa.historystages.data.lock.ZoneOrbitCamera;
import net.bananemdnsa.historystages.data.lock.ZoneShape;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * The tilted view: the terrain as a relief with the zone's shapes standing in it.
 *
 * <p>The scene is projected on the processor, but each corner keeps its distance and hands it to
 * the depth buffer. Sorting whole faces by the distance of their middle came first and was wrong in
 * a way that only showed up in the world: a wall running from a rooftop to the ground has a middle
 * that speaks for neither end, so a patch of ground could be judged nearer and paint over a wall
 * standing in front of it. Whole buildings lost their sides that way. Judged per pixel instead, the
 * question cannot be got wrong.
 *
 * <p>The buffer is cleared inside the map's own rectangle first. Everything the screen drew before
 * this sits at the very front, and without wiping it the scene would be found to be behind the
 * backdrop it is supposed to be drawn on. The scene is then kept behind the plane the interface
 * lives on, so the panels and the bar drawn afterwards stay on top of it without any further care.
 *
 * <p>See-through shapes still go back to front among themselves, with the buffer read but not
 * written: two translucent faces have to be blended in order, and no depth buffer can do that.
 */
public final class ZoneScene3d {

    /** Meridians and parallels of a sphere. Enough to read as round without doubling the face count. */
    private static final int SPHERE_MERIDIANS = 16;
    private static final int SPHERE_PARALLELS = 8;

    /** Segments around a cylinder. */
    private static final int CYLINDER_SEGMENTS = 24;

    private static final int SHAPE_FILL = 0x30FFCC00;
    private static final int SHAPE_FILL_HOT = 0x5AFFCC00;
    private static final int SHAPE_EDGE = 0x90FFCC00;
    private static final int SHAPE_EDGE_HOT = 0xFFFFCC00;

    private static final int PLAYER_FILL = 0xFFFFFFFF;
    private static final int PLAYER_EDGE = 0xC0000000;
    private static final int PLAYER_POST = 0xB0FFFFFF;

    /** Roughly how many pixels across the marker should read, whatever the zoom. */
    private static final double MARKER_PIXELS = 14;

    /** Edges are pulled a hair towards the eye so they do not fight the face they outline. */
    private static final float EDGE_BIAS = 0.6f;

    /** Room for the face index inside the sort key. A million faces is far past what is built. */
    private static final int INDEX_BITS = 20;
    private static final long INDEX_MASK = (1L << INDEX_BITS) - 1;

    /** Eight numbers a face for the corners on screen, four more for how far away each one is. */
    private float[] quadXY = new float[0];
    private float[] quadZ = new float[0];
    private int[] quadColour = new int[0];
    private long[] quadOrder = new long[0];
    private int quadCount;

    private float[] lineXY = new float[0];
    private float[] lineZ = new float[0];
    private int[] lineColour = new int[0];
    private int lineCount;

    /**
     * Rebuilds the picture. Call when the camera, the terrain or the shapes have changed — not per
     * frame; the arrays are drawn again as they stand.
     */
    public void build(ZoneOrbitCamera camera, List<ZoneShape> shapes, int selected,
                      boolean playerHere, double playerX, double playerY, double playerZ,
                      float playerYaw, int worldMinY, int worldMaxY) {
        quadCount = 0;
        lineCount = 0;

        for (int i = 0; i < shapes.size(); i++) {
            shape(camera, shapes.get(i), i == selected, worldMinY, worldMaxY);
        }
        if (playerHere) player(camera, playerX, playerY, playerZ, playerYaw);

        sortQuads();
    }

    public void draw(GuiGraphics g) {
        if (quadCount == 0 && lineCount == 0) return;

        Matrix4f matrix = g.pose().last().pose();
        g.flush();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        quads(matrix, true);

        // See-through faces read the buffer but do not add to it: written, the first one drawn
        // would hide the ones behind it that are meant to show through.
        RenderSystem.depthMask(false);
        quads(matrix, false);
        lines(matrix);

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** @param solid true for the faces that hide what is behind them, false for the rest */
    private void quads(Matrix4f matrix, boolean solid) {
        BufferBuilder buffer = null;

        // Solid faces are left to the buffer and go in any order; see-through ones still travel
        // back to front, which is what the sorted order is still kept for.
        for (int i = quadCount - 1; i >= 0; i--) {
            int quad = (int) (quadOrder[i] & INDEX_MASK);
            if (((quadColour[quad] >>> 24) == 0xFF) != solid) continue;

            if (buffer == null) {
                buffer = Tesselator.getInstance().begin(
                        VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            }
            int at = quad * 8;
            int atZ = quad * 4;
            for (int corner = 0; corner < 4; corner++) {
                vertex(buffer, matrix, quadXY[at + corner * 2], quadXY[at + corner * 2 + 1],
                        quadZ[atZ + corner], quadColour[quad]);
            }
        }

        if (buffer == null) return;
        MeshData mesh = buffer.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    private void lines(Matrix4f matrix) {
        if (lineCount == 0) return;

        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < lineCount; i++) {
            int at = i * 4;
            vertex(buffer, matrix, lineXY[at], lineXY[at + 1], lineZ[i * 2] + EDGE_BIAS,
                    lineColour[i]);
            vertex(buffer, matrix, lineXY[at + 2], lineXY[at + 3], lineZ[i * 2 + 1] + EDGE_BIAS,
                    lineColour[i]);
        }
        MeshData mesh = buffer.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z,
                               int argb) {
        buffer.addVertex(matrix, x, y, z)
                .setColor((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);
    }

    /**
     * Distance in the world turned into the screen's own idea of depth.
     *
     * <p>Larger means nearer here, which is the interface's convention and the opposite of the
     * scene's, hence the minus. The whole scene is pushed behind zero so that the panels and the
     * bar, drawn afterwards at zero, sit in front of it without being given a depth of their own.
     *
     * <p>The numbers come from {@link ZoneTerrainMesh} rather than being repeated: the landscape is
     * drawn from a matrix built on them, and a scene that placed its shapes on a different scale
     * would have them sink into ground they are standing on.
     */
    private static float screenZ(double depth) {
        return (float) Math.max(-900, Math.min(-20,
                -ZoneTerrainMesh.SCENE_PLANE - depth * ZoneTerrainMesh.DEPTH_SCALE));
    }

    // -------------------------------------------------------------------------------------
    // Shapes
    // -------------------------------------------------------------------------------------

    private void shape(ZoneOrbitCamera camera, ZoneShape shape, boolean hot,
                       int worldMinY, int worldMaxY) {
        int fill = hot ? SHAPE_FILL_HOT : SHAPE_FILL;
        int edge = hot ? SHAPE_EDGE_HOT : SHAPE_EDGE;

        switch (shape.type()) {
            case CUBE -> cube(camera, fill, edge,
                    Math.min(shape.fromX(), shape.toX()),
                    shape.fullHeight() ? worldMinY : Math.min(shape.fromY(), shape.toY()),
                    Math.min(shape.fromZ(), shape.toZ()),
                    Math.max(shape.fromX(), shape.toX()) + 1,
                    shape.fullHeight() ? worldMaxY : Math.max(shape.fromY(), shape.toY()) + 1,
                    Math.max(shape.fromZ(), shape.toZ()) + 1);
            case SPHERE -> sphere(camera, fill, edge, shape.fromX() + 0.5, shape.fromY() + 0.5,
                    shape.fromZ() + 0.5, shape.radius() + 0.5);
            case CYLINDER -> cylinder(camera, fill, edge, shape.fromX() + 0.5, shape.fromZ() + 0.5,
                    shape.radius() + 0.5,
                    shape.fullHeight() ? worldMinY : shape.fromY(),
                    shape.fullHeight() ? worldMaxY : shape.fromY() + shape.height());
        }
    }

    private void cube(ZoneOrbitCamera camera, int fill, int edge,
                      double x0, double y0, double z0, double x1, double y1, double z1) {
        addQuad(camera, fill, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        addQuad(camera, fill, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
        addQuad(camera, fill, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
        addQuad(camera, fill, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        addQuad(camera, fill, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        addQuad(camera, fill, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);

        double[][] lower = {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}};
        double[][] upper = {{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
        for (int i = 0; i < 4; i++) {
            int next = (i + 1) % 4;
            addLine(camera, edge, lower[i], lower[next]);
            addLine(camera, edge, upper[i], upper[next]);
            addLine(camera, edge, lower[i], upper[i]);
        }
    }

    private void sphere(ZoneOrbitCamera camera, int fill, int edge,
                        double cx, double cy, double cz, double r) {
        for (int parallel = 0; parallel < SPHERE_PARALLELS; parallel++) {
            double phi0 = Math.PI * parallel / SPHERE_PARALLELS;
            double phi1 = Math.PI * (parallel + 1) / SPHERE_PARALLELS;

            for (int meridian = 0; meridian < SPHERE_MERIDIANS; meridian++) {
                double theta0 = 2 * Math.PI * meridian / SPHERE_MERIDIANS;
                double theta1 = 2 * Math.PI * (meridian + 1) / SPHERE_MERIDIANS;

                double[] a = onSphere(cx, cy, cz, r, phi0, theta0);
                double[] b = onSphere(cx, cy, cz, r, phi0, theta1);
                double[] c = onSphere(cx, cy, cz, r, phi1, theta1);
                double[] d = onSphere(cx, cy, cz, r, phi1, theta0);
                addQuad(camera, fill, a[0], a[1], a[2], b[0], b[1], b[2],
                        c[0], c[1], c[2], d[0], d[1], d[2]);
            }
        }

        // Three great circles, so a sphere still reads as one when it is small on screen.
        circle(camera, edge, cx, cy, cz, r, 0);
        circle(camera, edge, cx, cy, cz, r, 1);
        circle(camera, edge, cx, cy, cz, r, 2);
    }

    private static double[] onSphere(double cx, double cy, double cz, double r,
                                     double phi, double theta) {
        double ring = Math.sin(phi) * r;
        return new double[] {cx + Math.cos(theta) * ring, cy + Math.cos(phi) * r,
                cz + Math.sin(theta) * ring};
    }

    /** @param plane 0 = level, 1 = through north and south, 2 = through east and west */
    private void circle(ZoneOrbitCamera camera, int colour,
                        double cx, double cy, double cz, double r, int plane) {
        double[] previous = null;
        for (int i = 0; i <= CYLINDER_SEGMENTS; i++) {
            double a = 2 * Math.PI * i / CYLINDER_SEGMENTS;
            double s = Math.sin(a) * r;
            double c = Math.cos(a) * r;
            double[] point = switch (plane) {
                case 0 -> new double[] {cx + c, cy, cz + s};
                case 1 -> new double[] {cx + c, cy + s, cz};
                default -> new double[] {cx, cy + s, cz + c};
            };
            if (previous != null) addLine(camera, colour, previous, point);
            previous = point;
        }
    }

    private void cylinder(ZoneOrbitCamera camera, int fill, int edge,
                          double cx, double cz, double r, double lowY, double highY) {
        for (int i = 0; i < CYLINDER_SEGMENTS; i++) {
            double a0 = 2 * Math.PI * i / CYLINDER_SEGMENTS;
            double a1 = 2 * Math.PI * (i + 1) / CYLINDER_SEGMENTS;
            double x0 = cx + Math.cos(a0) * r;
            double z0 = cz + Math.sin(a0) * r;
            double x1 = cx + Math.cos(a1) * r;
            double z1 = cz + Math.sin(a1) * r;

            addQuad(camera, fill, x0, lowY, z0, x1, lowY, z1, x1, highY, z1, x0, highY, z0);
            // Lid and floor as wedges from the middle; a quad with a repeated corner is a triangle.
            addQuad(camera, fill, cx, highY, cz, x0, highY, z0, x1, highY, z1, x1, highY, z1);
            addQuad(camera, fill, cx, lowY, cz, x0, lowY, z0, x1, lowY, z1, x1, lowY, z1);
        }

        circle(camera, edge, cx, lowY, cz, r, 0);
        circle(camera, edge, cx, highY, cz, r, 0);
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 2 * i;
            double x = cx + Math.cos(a) * r;
            double z = cz + Math.sin(a) * r;
            addLine(camera, edge, new double[] {x, lowY, z}, new double[] {x, highY, z});
        }
    }

    // -------------------------------------------------------------------------------------
    // Player
    // -------------------------------------------------------------------------------------

    /**
     * Where the player stands and which way they face.
     *
     * <p>An arrow lying on the ground with a thin post on it. Both are sized from the zoom so they
     * stay about the same size on screen — a marker fixed in blocks is a speck once the view covers
     * a few thousand of them, and a whole white building once it covers thirty.
     *
     * <p>Deliberately small. It is a marker on a map, not a character: the first version was scaled
     * to thirty pixels with a post two and a half times that, which at a distance turned into a
     * white box standing over the landscape and hid the very ground it was pointing at.
     */
    private void player(ZoneOrbitCamera camera, double x, double y, double z, float yawDegrees) {
        double size = Math.max(2, Math.min(96, MARKER_PIXELS * camera.blocksPerPixel()));

        // Yaw zero looks south, so the facing runs -sin, +cos — the same reading the flat map uses.
        double a = Math.toRadians(yawDegrees);
        double fx = -Math.sin(a);
        double fz = Math.cos(a);
        double rx = Math.cos(a);
        double rz = Math.sin(a);

        double ground = y + size * 0.02;
        double[] tip = {x + fx * size * 0.5, ground, z + fz * size * 0.5};
        double[] notch = {x - fx * size * 0.1, ground, z - fz * size * 0.1};
        double[] left = {x - fx * size * 0.3 - rx * size * 0.35, ground,
                z - fz * size * 0.3 - rz * size * 0.35};
        double[] right = {x - fx * size * 0.3 + rx * size * 0.35, ground,
                z - fz * size * 0.3 + rz * size * 0.35};

        // Tip, right, notch, left: a four-cornered dart. The two triangles it is split into are
        // exactly the two halves of an arrowhead, so no separate notch face is needed.
        addQuad(camera, PLAYER_FILL, tip[0], tip[1], tip[2], right[0], right[1], right[2],
                notch[0], notch[1], notch[2], left[0], left[1], left[2]);

        addLine(camera, PLAYER_EDGE, tip, right);
        addLine(camera, PLAYER_EDGE, right, notch);
        addLine(camera, PLAYER_EDGE, notch, left);
        addLine(camera, PLAYER_EDGE, left, tip);

        post(camera, x, y, z, size * 0.04, size * 0.9);
    }

    /**
     * A thin upright post, so the marker is still findable once the view is tipped towards the
     * horizon and the arrow on the ground has flattened to a line.
     */
    private void post(ZoneOrbitCamera camera, double x, double y, double z,
                      double halfWidth, double height) {
        double x0 = x - halfWidth;
        double x1 = x + halfWidth;
        double z0 = z - halfWidth;
        double z1 = z + halfWidth;
        double top = y + height;

        addQuad(camera, PLAYER_POST, x0, y, z0, x1, y, z0, x1, top, z0, x0, top, z0);
        addQuad(camera, PLAYER_POST, x0, y, z1, x1, y, z1, x1, top, z1, x0, top, z1);
        addQuad(camera, PLAYER_POST, x0, y, z0, x0, y, z1, x0, top, z1, x0, top, z0);
        addQuad(camera, PLAYER_POST, x1, y, z0, x1, y, z1, x1, top, z1, x1, top, z0);
    }

    // -------------------------------------------------------------------------------------
    // Buffers
    // -------------------------------------------------------------------------------------

    private void addQuad(ZoneOrbitCamera camera, int colour,
                         double ax, double ay, double az, double bx, double by, double bz,
                         double cx, double cy, double cz, double dx, double dy, double dz) {
        if (quadCount * 8 + 8 > quadXY.length) {
            int size = Math.max(4096, quadXY.length * 2);
            quadXY = Arrays.copyOf(quadXY, size);
            quadZ = Arrays.copyOf(quadZ, size / 2);
            quadColour = Arrays.copyOf(quadColour, size / 8);
            quadOrder = Arrays.copyOf(quadOrder, size / 8);
        }

        ZoneOrbitCamera.Projected a = camera.project(ax, ay, az);
        ZoneOrbitCamera.Projected b = camera.project(bx, by, bz);
        ZoneOrbitCamera.Projected c = camera.project(cx, cy, cz);
        ZoneOrbitCamera.Projected d = camera.project(dx, dy, dz);

        int at = quadCount * 8;
        quadXY[at] = a.x();
        quadXY[at + 1] = a.y();
        quadXY[at + 2] = b.x();
        quadXY[at + 3] = b.y();
        quadXY[at + 4] = c.x();
        quadXY[at + 5] = c.y();
        quadXY[at + 6] = d.x();
        quadXY[at + 7] = d.y();

        int atZ = quadCount * 4;
        quadZ[atZ] = screenZ(a.depth());
        quadZ[atZ + 1] = screenZ(b.depth());
        quadZ[atZ + 2] = screenZ(c.depth());
        quadZ[atZ + 3] = screenZ(d.depth());

        quadColour[quadCount] = colour;
        quadOrder[quadCount] = sortKey((float) ((a.depth() + b.depth() + c.depth() + d.depth()) / 4),
                quadCount);
        quadCount++;
    }

    private void addLine(ZoneOrbitCamera camera, int colour, double[] from, double[] to) {
        if (lineCount * 4 + 4 > lineXY.length) {
            int size = Math.max(2048, lineXY.length * 2);
            lineXY = Arrays.copyOf(lineXY, size);
            lineZ = Arrays.copyOf(lineZ, size / 2);
            lineColour = Arrays.copyOf(lineColour, size / 4);
        }

        ZoneOrbitCamera.Projected a = camera.project(from[0], from[1], from[2]);
        ZoneOrbitCamera.Projected b = camera.project(to[0], to[1], to[2]);

        int at = lineCount * 4;
        lineXY[at] = a.x();
        lineXY[at + 1] = a.y();
        lineXY[at + 2] = b.x();
        lineXY[at + 3] = b.y();
        lineZ[lineCount * 2] = screenZ(a.depth());
        // Edges are nudged a hair towards the eye, otherwise they fight with the very face they
        // outline and come out dashed.
        lineZ[lineCount * 2 + 1] = screenZ(b.depth());
        lineColour[lineCount] = colour;
        lineCount++;
    }

    private void sortQuads() {
        if (quadCount > 1) Arrays.sort(quadOrder, 0, quadCount);
    }

    /**
     * Distance in the upper bits, index in the lower, so one primitive sort orders the faces.
     *
     * <p>Two traps in one line. The bit pattern of a float only counts upwards while the float is
     * positive; below zero it runs backwards, so the sign bit is flipped on positives and every bit
     * on negatives, which turns it into a number that sorts the way the value does. And the result
     * is kept unsigned and shifted only twenty places — pushed the full thirty-two, half the keys
     * would land on the sign bit of the {@code long} and sort ahead of everything else, putting the
     * far half of the picture in front.
     */
    private static long sortKey(float depth, int index) {
        int bits = Float.floatToIntBits(depth);
        bits ^= (bits >> 31) | 0x80000000;
        return ((bits & 0xFFFFFFFFL) << INDEX_BITS) | (index & INDEX_MASK);
    }
}
