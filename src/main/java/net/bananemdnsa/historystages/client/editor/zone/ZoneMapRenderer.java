package net.bananemdnsa.historystages.client.editor.zone;

import java.util.List;

import net.bananemdnsa.historystages.data.lock.ZoneMapView;
import net.bananemdnsa.historystages.data.lock.ZoneShape;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.Font;
import com.mojang.math.Axis;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The flat map, painted.
 *
 * <p>Holds nothing. Camera, terrain and selection all come from outside, so the thumbnail in the
 * tab and the full-screen map can never disagree about where a shape is — they are the same
 * drawing at two sizes, fed by the same arithmetic.
 */
public final class ZoneMapRenderer {

    private static final int BACKDROP = 0xFF141419;
    private static final int BORDER = 0xFF2A2A2A;
    private static final int GRID_MINOR = 0x14FFFFFF;
    private static final int GRID_MAJOR = 0x40FFFFFF;
    private static final int GRID_LABEL = 0xFF9A9A9A;

    private static final int SHAPE_LINE = 0xB3FFCC00;
    private static final int SHAPE_FILL = 0x1AFFCC00;
    private static final int HOT_LINE = 0xFFFFCC00;
    private static final int HOT_FILL = 0x38FFCC00;

    /** Vanilla's own map markers, so the arrow on this map is the arrow players already know. */
    private static final ResourceLocation MARKER_ICON =
            ResourceLocation.withDefaultNamespace("textures/map/decorations/player.png");
    private static final ResourceLocation MARKER_OFF_MAP =
            ResourceLocation.withDefaultNamespace("textures/map/decorations/player_off_map.png");

    /** The icon is eight pixels; a little larger reads better against terrain. */
    private static final float MARKER_SCALE = 1.5f;

    private static final int SCALE_BAR = 0xFFDDDDDD;

    /** How far the clamped player arrow stays from the frame, so its tip is never cut off. */
    private static final int CLAMP_INSET = 6;

    private ZoneMapRenderer() {}

    public static void backdrop(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, BACKDROP);
        g.fill(x, y, x + w, y + 1, BORDER);
        g.fill(x, y + h - 1, x + w, y + h, BORDER);
        g.fill(x, y, x + 1, y + h, BORDER);
        g.fill(x + w - 1, y, x + w, y + h, BORDER);
    }

    /**
     * The sampled terrain, blitted where it belongs in the world rather than into the box.
     *
     * <p>Between two samplings the map may be dragged on, and the picture has to travel with it —
     * pinning it to the box instead would make the ground slide under the shapes.
     */
    public static void terrain(GuiGraphics g, int x, int y, ZoneMapView view,
                               ZoneTerrainTexture texture, ZoneTerrainSampler.Surface surface) {
        if (texture == null || texture.isEmpty() || surface == null) return;

        int x0 = x + Math.round(view.screenX(surface.minX()));
        int y0 = y + Math.round(view.screenZ(surface.minZ()));
        int x1 = x + Math.round(view.screenX(surface.minX() + (double) surface.cols() * surface.step()));
        int y1 = y + Math.round(view.screenZ(surface.minZ() + (double) surface.rows() * surface.step()));
        if (x1 <= x0 || y1 <= y0) return;

        g.blit(texture.id(), x0, y0, x1 - x0, y1 - y0,
                0, 0, texture.cols(), texture.rows(), texture.cols(), texture.rows());
    }

    /** @param labels whether the major lines carry their coordinate; off on the thumbnail, it is too small */
    public static void grid(GuiGraphics g, Font font, int x, int y, int w, int h,
                            ZoneMapView view, boolean labels) {
        int minor = view.minorGridStep();
        int major = view.majorGridStep();

        long firstX = (long) Math.ceil(view.worldMinX() / minor) * minor;
        for (long wx = firstX; wx < view.worldMaxX(); wx += minor) {
            int px = x + Math.round(view.screenX(wx));
            boolean big = Math.floorMod(wx, major) == 0;
            g.fill(px, y + 1, px + 1, y + h - 1, big ? GRID_MAJOR : GRID_MINOR);
            if (labels && big) {
                g.drawString(font, Long.toString(wx), px + 2, y + 3, GRID_LABEL, false);
            }
        }

        long firstZ = (long) Math.ceil(view.worldMinZ() / minor) * minor;
        for (long wz = firstZ; wz < view.worldMaxZ(); wz += minor) {
            int pz = y + Math.round(view.screenZ(wz));
            boolean big = Math.floorMod(wz, major) == 0;
            g.fill(x + 1, pz, x + w - 1, pz + 1, big ? GRID_MAJOR : GRID_MINOR);
            if (labels && big) {
                g.drawString(font, Long.toString(wz), x + 3, pz + 2, GRID_LABEL, false);
            }
        }
    }

    public static void shapes(GuiGraphics g, Font font, int x, int y, ZoneMapView view,
                              List<ZoneShape> shapes, int selected, boolean numbers) {
        // The selected one goes last so a shape overlapping it cannot paint over it.
        for (int i = 0; i < shapes.size(); i++) {
            if (i != selected) draw(g, font, x, y, view, shapes.get(i), i, false, numbers);
        }
        if (selected >= 0 && selected < shapes.size()) {
            draw(g, font, x, y, view, shapes.get(selected), selected, true, numbers);
        }
    }

    private static void draw(GuiGraphics g, Font font, int ox, int oy, ZoneMapView view,
                             ZoneShape shape, int index, boolean hot, boolean numbers) {
        ZoneMapView.Rect r = view.rectFor(shape);
        int line = hot ? HOT_LINE : SHAPE_LINE;
        int fill = hot ? HOT_FILL : SHAPE_FILL;

        if (r.round()) {
            ellipse(g, ox + r.x(), oy + r.y(), r.w(), r.h(), line, fill);
        } else {
            rect(g, ox + r.x(), oy + r.y(), r.w(), r.h(), line, fill);
        }

        if (numbers) {
            String label = Integer.toString(index + 1);
            int cx = Math.round(ox + r.x() + r.w() / 2f) - font.width(label) / 2;
            int cy = Math.round(oy + r.y() + r.h() / 2f) - 4;
            g.drawString(font, label, cx, cy, line, true);
        }
    }

    private static void rect(GuiGraphics g, float fx, float fy, float fw, float fh,
                             int line, int fill) {
        int x0 = Math.round(fx);
        int y0 = Math.round(fy);
        // A zone one block wide still has to be visible, so an empty box is widened to one pixel.
        int x1 = Math.max(x0 + 1, Math.round(fx + fw));
        int y1 = Math.max(y0 + 1, Math.round(fy + fh));

        g.fill(x0, y0, x1, y1, fill);
        g.fill(x0, y0, x1, y0 + 1, line);
        g.fill(x0, y1 - 1, x1, y1, line);
        g.fill(x0, y0, x0 + 1, y1, line);
        g.fill(x1 - 1, y0, x1, y1, line);
    }

    /**
     * A circle out of horizontal spans.
     *
     * <p>There is no circle primitive here, and a run of one-pixel rows costs less than a texture
     * for something this small. The first and last row are drawn entirely in the outline colour —
     * they are the cap of the circle, and leaving them filled makes the top and bottom look open.
     */
    private static void ellipse(GuiGraphics g, float fx, float fy, float fw, float fh,
                                int line, int fill) {
        int rows = Math.max(1, Math.round(fh));
        float rx = Math.max(0.5f, fw / 2f);
        float ry = Math.max(0.5f, fh / 2f);
        float cx = fx + rx;
        int top = Math.round(fy);

        for (int row = 0; row < rows; row++) {
            float dy = (row + 0.5f - ry) / ry;
            if (dy < -1 || dy > 1) continue;
            float half = rx * (float) Math.sqrt(Math.max(0, 1 - dy * dy));
            int left = Math.round(cx - half);
            int right = Math.max(left + 1, Math.round(cx + half));
            int rowY = top + row;

            if (row == 0 || row == rows - 1) {
                g.fill(left, rowY, right, rowY + 1, line);
                continue;
            }
            g.fill(left, rowY, right, rowY + 1, fill);
            g.fill(left, rowY, left + 1, rowY + 1, line);
            g.fill(right - 1, rowY, right, rowY + 1, line);
        }
    }

    /**
     * The player as an arrow with a facing.
     *
     * <p>Vanilla's own map marker, turned, rather than an arrow drawn line by line. Drawing it by
     * hand was tried twice — hollow first, then filled — and both read as a smudge at eight pixels
     * across, because a shape that small lives or dies by its pixels and those were being worked out
     * afresh from angles every frame. The game already ships the picture that belongs on a map, and
     * a picture only has to be turned.
     *
     * <p>Outside the view it becomes the marker vanilla uses for exactly that case and sticks to the
     * frame, rather than vanishing and leaving no way to tell "far away" from "wrong world".
     *
     * @param yawDegrees the player's yaw as Minecraft keeps it
     * @return true if the marker had to be clamped, so the caller can write the distance beside it
     */
    public static boolean player(GuiGraphics g, int x, int y, int w, int h, ZoneMapView view,
                                 double worldX, double worldZ, float yawDegrees) {
        float px = x + view.screenX(worldX);
        float pz = y + view.screenZ(worldZ);
        float cx = Math.max(x + CLAMP_INSET, Math.min(x + w - CLAMP_INSET, px));
        float cz = Math.max(y + CLAMP_INSET, Math.min(y + h - CLAMP_INSET, pz));
        boolean clamped = cx != px || cz != pz;

        // Yaw zero faces south, which is downwards on a map, and the marker is drawn pointing up.
        // The two zero points sit opposite each other, so the offset is exactly half a turn.
        g.pose().pushPose();
        g.pose().translate(cx, cz, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(yawDegrees + 180));
        g.pose().scale(MARKER_SCALE, MARKER_SCALE, 1);
        g.blit(clamped ? MARKER_OFF_MAP : MARKER_ICON, -4, -4, 8, 8, 0, 0, 8, 8, 8, 8);
        g.pose().popPose();

        return clamped;
    }

    public static void scaleBar(GuiGraphics g, Font font, int x, int y, int w, int h,
                                ZoneMapView view) {
        int blocks = view.scaleBarBlocks(Math.min(90, w / 3));
        int px = Math.max(4, (int) Math.round(blocks / view.blocksPerPixel()));
        int bx = x + w - px - 8;
        int by = y + h - 10;

        g.fill(bx, by, bx + px, by + 2, SCALE_BAR);
        g.drawString(font, Integer.toString(blocks), bx, by - 10, GRID_LABEL, true);
    }
}
