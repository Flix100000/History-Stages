package net.bananemdnsa.historystages.client.editor.graph;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Drawing helpers for the graph's outline-ring node shapes. Each shape is rendered from a
 * smooth runtime texture (see {@link NodeTextures}): the {@code border} colour fills the
 * full shape, then the {@code fill} colour is drawn slightly smaller, leaving a clean
 * coloured ring of width {@code bw} around a solid interior. Public signatures are kept so
 * call sites in the graph screen and legend are unchanged.
 */
public final class NodeShapes {

    /** Dark interior colour for the small checkmark badge's outer ring. */
    public static final int CHECKMARK_RING_COLOR = 0xFF17171A;

    private NodeShapes() {}

    /**
     * Draws whichever shape {@code shape} names, falling back to {@link #rect} for an unknown
     * one. The single place that maps a style's shape name onto a draw call, so the graph canvas
     * and the editor's style preview cannot end up drawing different things.
     */
    public static void draw(GuiGraphics g, String shape, int cx, int cy, int r,
                            int fill, int border, int bw) {
        draw(null, g, shape, cx, cy, r, fill, border, bw);
    }

    /** As {@link #draw(GuiGraphics, String, int, int, int, int, int, int)}, into {@code batch} when it is not null. */
    static void draw(QuadBatch batch, GuiGraphics g, String shape, int cx, int cy, int r,
                     int fill, int border, int bw) {
        ResourceLocation tex = switch (shape) {
            case "CIRCLE" -> NodeTextures.circle();
            case "DIAMOND" -> NodeTextures.diamond();
            case "HEXAGON" -> NodeTextures.hexagon();
            case "ROUNDED" -> NodeTextures.rounded();
            default -> NodeTextures.rect(); // RECT, or anything unknown
        };
        ring(batch, g, tex, cx, cy, r, fill, border, bw);
    }

    /** Small status-tick badge in the node's bottom-right corner. */
    public static void checkmark(GuiGraphics g, int cx, int cy, int r, int badgeColor) {
        checkmark(null, g, cx, cy, r, badgeColor);
    }

    private static void checkmark(QuadBatch batch, GuiGraphics g, int cx, int cy, int r, int badgeColor) {
        int br = Math.max(4, Math.round(r * 0.45f));
        int bx = cx + r - br / 2;
        int by = cy + r - br / 2;
        int ringW = Math.max(1, Math.round(br * 0.22f));
        ring(batch, g, NodeTextures.circle(), bx, by, br, badgeColor, CHECKMARK_RING_COLOR, ringW);

        // One generated glyph, like every other shape on this canvas. Stroking it at render time
        // meant either a bare corner where the arms meet, or a round cap patching that corner
        // that is wider than the stroke itself — the tick was the only thing here still being
        // drawn by hand instead of blitted, and it looked like it.
        int glyph = Math.max(3, Math.round(br * 1.6f));
        box(batch, g, NodeTextures.check(), bx - glyph / 2, by - glyph / 2, glyph, glyph, 0xFFFFFFFF);
    }

    /**
     * The status badge a style's {@code checkmark} switch turns on: a tick in general, the
     * no-entry sign for a blocked stage. One switch, so the node style screens need no second one.
     */
    public static void statusBadge(GuiGraphics g, net.bananemdnsa.historystages.data.graph.NodeState state,
                                   int cx, int cy, int r, int badgeColor) {
        statusBadge(null, g, state, cx, cy, r, badgeColor);
    }

    static void statusBadge(QuadBatch batch, GuiGraphics g, net.bananemdnsa.historystages.data.graph.NodeState state,
                            int cx, int cy, int r, int badgeColor) {
        if (state == net.bananemdnsa.historystages.data.graph.NodeState.BLOCKED) {
            blockedBadge(batch, g, cx, cy, r);
        } else {
            checkmark(batch, g, cx, cy, r, badgeColor);
        }
    }

    /**
     * The "blocked" badge: a red disc with a white bar, top right, where the tick sits bottom
     * right. Drawn from shapes rather than a font glyph, which the default font may not have.
     */
    public static void blockedBadge(GuiGraphics g, int cx, int cy, int r) {
        blockedBadge(null, g, cx, cy, r);
    }

    private static void blockedBadge(QuadBatch batch, GuiGraphics g, int cx, int cy, int r) {
        int br = Math.max(4, Math.round(r * 0.45f));
        int bx = cx + r - br / 2;
        int by = cy - r + br / 2;
        int ringW = Math.max(1, Math.round(br * 0.22f));
        ring(batch, g, NodeTextures.circle(), bx, by, br, 0xFFCC2222, CHECKMARK_RING_COLOR, ringW);
        int half = Math.max(2, Math.round(br * 0.55f));
        int barH = Math.max(1, Math.round(br * 0.28f));
        int barTop = by - barH / 2 - barH % 2 + 1;
        int barBottom = by + barH / 2 + 1;
        if (batch != null) {
            // The rect texture is solid edge to edge, so it stands in for a fill — and keeps the
            // bar in the same batch as the disc it sits on, instead of drawing before it.
            batch.rect(NodeTextures.rect(), g.pose().last().pose(), bx - half, barTop, bx + half, barBottom, 0xFFFFFFFF);
        } else {
            g.fill(bx - half, barTop, bx + half, barBottom, 0xFFFFFFFF);
        }
    }

    public static void circle(GuiGraphics g, int cx, int cy, int r, int fill, int border, int bw) {
        ring(null, g, NodeTextures.circle(), cx, cy, r, fill, border, bw);
    }

    public static void diamond(GuiGraphics g, int cx, int cy, int r, int fill, int border, int bw) {
        ring(null, g, NodeTextures.diamond(), cx, cy, r, fill, border, bw);
    }

    public static void hexagon(GuiGraphics g, int cx, int cy, int r, int fill, int border, int bw) {
        ring(null, g, NodeTextures.hexagon(), cx, cy, r, fill, border, bw);
    }

    public static void rect(GuiGraphics g, int cx, int cy, int r, int fill, int border, int bw) {
        ring(null, g, NodeTextures.rect(), cx, cy, r, fill, border, bw);
    }

    /**
     * The corner radius is baked into {@link NodeTextures#rounded()} at a fixed relative value
     * (0.18 of the texture's side) and cannot change per draw call — a texture generated once
     * cannot change shape at runtime. The config's {@code cornerRadius} therefore does NOT scale
     * the corner geometry; only {@code r} (the drawn size of the whole shape) is under caller
     * control here.
     */
    public static void rounded(GuiGraphics g, int cx, int cy, int r, int fill, int border, int bw) {
        ring(null, g, NodeTextures.rounded(), cx, cy, r, fill, border, bw);
    }

    private static void ring(QuadBatch batch, GuiGraphics g, ResourceLocation tex, int cx, int cy, int r,
                             int fill, int border, int bw) {
        if (r <= 0) return;
        box(batch, g, tex, cx - r, cy - r, r * 2, r * 2, border);
        int inner = r - Math.max(1, bw);
        if (inner > 0) box(batch, g, tex, cx - inner, cy - inner, inner * 2, inner * 2, fill);
    }

    /** The whole of {@code tex} in a {@code w x h} box at (x,y), tinted {@code color}. */
    private static void box(QuadBatch batch, GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h, int color) {
        if (batch != null) {
            batch.rect(tex, g.pose().last().pose(), x, y, x + w, y + h, color);
        } else {
            NodeTextures.blit(g, tex, x, y, w, h, NodeTextures.SIZE, NodeTextures.SIZE, color);
        }
    }
}
