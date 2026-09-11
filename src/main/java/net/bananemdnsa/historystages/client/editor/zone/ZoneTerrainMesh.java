package net.bananemdnsa.historystages.client.editor.zone;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.bananemdnsa.historystages.data.lock.ZoneOrbitCamera;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/**
 * The landscape of the tilted view, built once and then only looked at from different angles.
 *
 * <p>This is the point of the class. The ground was first worked out afresh on every frame of a
 * drag — every face projected by hand, every time — which is a great deal of arithmetic to answer a
 * question that has not changed. What changes while you turn is the angle, not the landscape. So
 * the ground is handed to the graphics card once in world coordinates and afterwards only given a
 * new matrix, and turning costs nothing at all.
 *
 * <p>It follows that the detail is no longer paid for per frame. The grid can be as fine as the
 * measurements allow, because a finer mesh only costs the one upload.
 *
 * <p>Rebuilt when the measurements change — a coarser or finer grid, or holes filled in as chunks
 * arrive — and never for a change of view.
 */
public final class ZoneTerrainMesh implements AutoCloseable {

    /** Tallest piece a drop is drawn in, in blocks. Keeps a single face from spanning a whole cliff. */
    private static final int WALL_SLICE = 16;

    private VertexBuffer buffer;
    private boolean empty = true;

    /**
     * Where the whole scene sits in the screen's own depth, and how much of that depth a block of
     * distance is worth.
     *
     * <p>Shared with the hand-drawn part of the scene, which has to land in the same depth for the
     * two to interleave. Behind zero because the panels and the bar are drawn afterwards at zero
     * and have to stay in front; the scale is small enough that even a map thousands of blocks
     * across cannot push a corner of it past that plane.
     */
    public static final double SCENE_PLANE = 400;
    public static final double DEPTH_SCALE = 0.1;

    public boolean isEmpty() {
        return empty;
    }

    /** @param surface the measured ground, or null to leave nothing to draw */
    public void upload(ZoneTerrainSampler.Surface surface) {
        if (surface == null) {
            empty = true;
            return;
        }

        BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = build(builder, surface);

        MeshData mesh = builder.build();
        if (mesh == null || !any) {
            if (mesh != null) mesh.close();
            empty = true;
            return;
        }

        if (buffer == null) buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(mesh);
        VertexBuffer.unbind();
        empty = false;
    }

    public void draw(GuiGraphics g, ZoneOrbitCamera camera, int mapX, int mapY) {
        if (empty || buffer == null) return;

        // The screen's own model-view has to come first, and leaving it out is why this drew
        // nothing at all for a while. The interface is rendered through a projection whose near
        // plane sits a thousand units out, and the stack carries a translation of some eleven
        // thousand to push everything into that range. Handing the card a matrix without it puts
        // the whole landscape in front of the near plane, where it is clipped away before anything
        // is shaded — faces built in their thousands, nothing on screen. The immediate path never
        // showed this because it passes the same stack for you.
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        modelView.mul(g.pose().last().pose());
        modelView.translate(mapX, mapY, 0);
        modelView.mul(worldToScreen(camera));

        g.flush();

        // Every piece of state this needs is set here rather than assumed. Back-face culling is the
        // one that bites: a top face wound the way this builds it faces away from the camera under
        // the default rule, so with culling left on the entire landscape is thrown away and the view
        // comes up empty. It used to be switched off by the hand-drawn part, which no longer runs
        // first.
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        buffer.bind();
        buffer.drawWithShader(modelView, RenderSystem.getProjectionMatrix(),
                GameRenderer.getPositionColorShader());
        VertexBuffer.unbind();

        RenderSystem.enableCull();
    }

    /**
     * World coordinates to the map's own pixels, as one matrix.
     *
     * <p>Deliberately the same arithmetic {@code ZoneOrbitCamera#project} does by hand, only
     * written so the graphics card can apply it: across and along the screen divided by the scale,
     * distance turned into screen depth. If the two ever disagree, a click lands somewhere other
     * than where the ground was drawn.
     */
    public static Matrix4f worldToScreen(ZoneOrbitCamera camera) {
        ZoneOrbitCamera.Vec right = camera.right();
        ZoneOrbitCamera.Vec up = camera.up();
        ZoneOrbitCamera.Vec forward = camera.forward();

        // Columns, not rows: the first four numbers are the first column of the matrix.
        Matrix4f basis = new Matrix4f(
                (float) right.x(), (float) up.x(), (float) forward.x(), 0,
                (float) right.y(), (float) up.y(), (float) forward.y(), 0,
                (float) right.z(), (float) up.z(), (float) forward.z(), 0,
                0, 0, 0, 1);

        float scale = (float) (1 / camera.blocksPerPixel());
        Matrix4f m = new Matrix4f();
        m.translate(camera.width() / 2f, camera.height() / 2f, (float) -SCENE_PLANE);
        m.scale(scale, -scale, (float) -DEPTH_SCALE);
        m.mul(basis);
        m.translate((float) -camera.centreX(), (float) -camera.centreY(), (float) -camera.centreZ());
        return m;
    }

    /**
     * The sampled heights as a field of blocks.
     *
     * <p>Flat tops at whole heights with upright walls between them, not a smoothed sheet. Averaging
     * the corners was the first attempt and it closed the gaps, but it also turned Minecraft into
     * rolling hills — the terracing is what a landscape is read by, and without it the picture stops
     * looking like the game it belongs to.
     *
     * <p>Neighbouring cells of the same height and colour are merged into one wide face. Runs along
     * one axis only: the second would have to track which cells are already spoken for, and ground
     * that is flat in both directions at once is rare enough that the bookkeeping would not earn
     * its keep.
     *
     * <p>Walls go only towards the neighbour to the east and the one to the south. Every step in the
     * ground is shared by two cells, so asking both would draw each wall twice.
     */
    private static boolean build(BufferBuilder builder, ZoneTerrainSampler.Surface surface) {
        int cols = surface.cols();
        int rows = surface.rows();
        int step = surface.step();
        boolean any = false;

        for (int row = 0; row < rows; row++) {
            double z0 = surface.minZ() + (double) row * step;
            double z1 = z0 + step;

            int col = 0;
            while (col < cols) {
                if (!surface.known(col, row)) {
                    col++;
                    continue;
                }

                int height = surface.heightAt(col, row);
                int colour = top(surface, col, row);

                int run = col;
                while (run < cols && surface.known(run, row)
                        && surface.heightAt(run, row) == height
                        && top(surface, run, row) == colour) {
                    run++;
                }

                double x0 = surface.minX() + (double) col * step;
                double x1 = surface.minX() + (double) run * step;
                quad(builder, colour, x0, height, z0, x1, height, z0, x1, height, z1, x0, height, z1);
                any = true;

                southWall(builder, surface, col, run, row, step, height, colour, z1);
                if (run < cols && surface.known(run, row)) {
                    eastWall(builder, surface, run, row, height, colour, x1, z0, z1);
                }
                col = run;
            }
        }
        return any;
    }

    /**
     * The drop along the southern edge of a run.
     *
     * <p>Split again by the neighbour's height rather than taken as one: the run above it is flat,
     * but the ground below it need not be, and a single face spanning two different drops would
     * hang in the air over the shallower half.
     */
    private static void southWall(BufferBuilder builder, ZoneTerrainSampler.Surface surface,
                                  int from, int to, int row, int step,
                                  int height, int colour, double z) {
        if (row + 1 >= surface.rows()) return;

        int col = from;
        while (col < to) {
            if (!surface.known(col, row + 1)) {
                col++;
                continue;
            }

            int south = surface.heightAt(col, row + 1);
            int run = col;
            while (run < to && surface.known(run, row + 1)
                    && surface.heightAt(run, row + 1) == south) {
                run++;
            }

            if (south != height) {
                double x0 = surface.minX() + (double) col * step;
                double x1 = surface.minX() + (double) run * step;
                boolean southHigher = south > height;
                int face = wall(surface, southHigher ? col : from, southHigher ? row + 1 : row,
                        southHigher ? top(surface, col, row + 1) : colour);
                slices(builder, darken(face), Math.min(height, south), Math.max(height, south),
                        x0, z, x1, z);
            }
            col = run;
        }
    }

    /**
     * The drop along the eastern edge of a run.
     *
     * <p>Painted in what the higher side exposes and darkened, the way a block's side is darker than
     * its top. Take the lower side's colour instead and a cliff wears the colour of the valley
     * floor, which reads as a hole rather than as a drop.
     */
    private static void eastWall(BufferBuilder builder, ZoneTerrainSampler.Surface surface,
                                 int neighbourCol, int neighbourRow, int height, int colour,
                                 double x, double z0, double z1) {
        int other = surface.heightAt(neighbourCol, neighbourRow);
        if (other == height) return;

        boolean neighbourHigher = other > height;
        int face = wall(surface, neighbourHigher ? neighbourCol : neighbourCol - 1, neighbourRow,
                neighbourHigher ? top(surface, neighbourCol, neighbourRow) : colour);

        slices(builder, darken(face), Math.min(height, other), Math.max(height, other),
                x, z0, x, z1);
    }

    /** A drop cut into stacked pieces, so no single face spans a whole cliff. */
    private static void slices(BufferBuilder builder, int colour, double bottom, double top,
                               double ax, double az, double bx, double bz) {
        for (double y = bottom; y < top; y += WALL_SLICE) {
            double upper = Math.min(top, y + WALL_SLICE);
            quad(builder, colour, ax, y, az, bx, y, bz, bx, upper, bz, ax, upper, az);
        }
    }

    private static int top(ZoneTerrainSampler.Surface surface, int col, int row) {
        return abgrToArgb(surface.abgrAt(col, row));
    }

    /** What the taller side exposes, falling back to its top colour where nothing was measured. */
    private static int wall(ZoneTerrainSampler.Surface surface, int col, int row, int fallback) {
        if (col < 0 || row < 0 || col >= surface.cols() || row >= surface.rows()) return fallback;

        int side = surface.sideAbgrAt(col, row);
        return side == 0 ? fallback : abgrToArgb(side);
    }

    /** The sampler keeps blue, green, red the way the picture wants it; a vertex wants the reverse. */
    private static int abgrToArgb(int abgr) {
        return (abgr & 0xFF000000)
                | ((abgr & 0x000000FF) << 16)
                | (abgr & 0x0000FF00)
                | ((abgr >> 16) & 0xFF);
    }

    /** A side face, at three quarters of the light — the same ratio the game itself uses. */
    private static int darken(int argb) {
        return (argb & 0xFF000000)
                | (((argb >> 16) & 0xFF) * 3 / 4) << 16
                | (((argb >> 8) & 0xFF) * 3 / 4) << 8
                | ((argb & 0xFF) * 3 / 4);
    }

    private static void quad(BufferBuilder builder, int argb,
                             double ax, double ay, double az, double bx, double by, double bz,
                             double cx, double cy, double cz, double dx, double dy, double dz) {
        vertex(builder, ax, ay, az, argb);
        vertex(builder, bx, by, bz, argb);
        vertex(builder, cx, cy, cz, argb);
        vertex(builder, dx, dy, dz, argb);
    }

    private static void vertex(BufferBuilder builder, double x, double y, double z, int argb) {
        builder.addVertex((float) x, (float) y, (float) z)
                .setColor((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);
    }

    @Override
    public void close() {
        if (buffer != null) {
            buffer.close();
            buffer = null;
        }
        empty = true;
    }
}
