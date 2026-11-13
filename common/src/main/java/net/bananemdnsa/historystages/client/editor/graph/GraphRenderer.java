package net.bananemdnsa.historystages.client.editor.graph;

import com.mojang.math.Axis;
import net.bananemdnsa.historystages.GraphConfig;
import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.bananemdnsa.historystages.data.graph.ResolvedCanvasBackground;
import net.bananemdnsa.historystages.data.graph.ResolvedStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Draws the stage graph: background, grid, edges, nodes, captions and the focus ring, and answers
 * which node sits under a point. Reads the camera and the model; changes neither.
 */
final class GraphRenderer {

    /** Resolved icon items, keyed by the raw id from the stage definition. */
    private final Map<String, ItemStack> iconCache = new HashMap<>();

    /**
     * Largest {@code size} multiplier {@code graph.toml} accepts on a style block. Used only to
     * bound culling — if that range is ever widened, widen this with it or nodes at the very
     * edge of the window will start popping.
     */
    private static final float MAX_STYLE_SIZE = 4.0f;

    /** Extra room below a node for its label when deciding whether it is off screen. */
    private static final int LABEL_CULL_PAD = 24;

    /** Thinnest a line is ever drawn. Sub-pixel is fine now that the quad uses float corners. */
    private static final float MIN_LINE_H = 1.0f;

    /** Grid line colour — one pixel wide, faint on purpose. */
    private static final int GRID_COLOR = 0x1FFFFFFF;

    /**
     * Smallest gap between drawn grid lines, in screen pixels. Once the cell size falls below
     * this the grid draws every second cell instead, then every fourth, and so on.
     */
    private static final int MIN_GRID_SPACING = 26;

    /** Stage node radius in screen pixels at zoom 1.0, before the style's {@code size} multiplier. */
    static final int BASE_NODE_RADIUS = 15;
    /** Below this zoom, node captions are hidden — matches the legacy graph screen's threshold. */
    private static final float LABEL_HIDE_ZOOM = 0.55f;
    /** Extra pixels added to a node's drawn radius when hit-testing, so an edge click still counts. */
    private static final int HIT_PADDING = 2;
    /** Peak scale-up applied to the hovered node, eased in via {@link Ease#outCubic}. */
    private static final float HOVER_LIFT = 0.18f;
    /** Number of straight sub-segments a CURVED edge is sampled into. */
    private static final int BEZIER_SEGMENTS = 16;
    /** Accent used for the focused-node highlight ring, matching the editor's gold accent. */
    private static final int FOCUS_RING_COLOR = 0xFFFFCC00;

    /** Shared hover-lift progress (0..1), applied only to whichever node is under the cursor. */
    private final Anim hoverAnim = new Anim();

    private final GraphCamera camera;
    private StageGraphModel model;
    /** Snapshot of {@code model.nodes()} in insertion (= draw) order, cached for hit testing. */
    private List<Map.Entry<String, StageGraphModel.Node>> drawOrder = List.of();

    GraphRenderer(GraphCamera camera) {
        this.camera = camera;
    }

    void setModel(StageGraphModel model) {
        this.model = model;
        refreshDrawOrder();
    }

    /** After a node was moved or added in the model in place. */
    void refreshDrawOrder() {
        this.drawOrder = model == null ? List.of() : new ArrayList<>(model.nodes().entrySet());
    }

    /**
     * Everything that pans and zooms with the map. {@code selected} nodes get the same ring as the
     * focused one. {@code overlay} runs inside the same clip and camera transform, after the
     * nodes — the interaction draws its drop targets there.
     */
    void renderMap(GuiGraphics g, Font font, ResolvedCanvasBackground bg, int mouseX, int mouseY,
                   String focusedKey, Set<String> selected, Runnable overlay) {
        int right = Math.min(camera.viewX() + camera.viewW(), camera.clipRight());
        if (right <= camera.viewX()) return;
        g.enableScissor(camera.viewX(), camera.viewY(), right, camera.viewY() + camera.viewH());
        g.fill(camera.viewX(), camera.viewY(), right, camera.viewY() + camera.viewH(), 0xFF000000 | bg.rgb());

        // Everything in graph space shares one integer pixel grid (see screenX/screenY) and then
        // gets the camera's sub-pixel remainder applied once, here. Letting each node round its
        // own position instead means they cross pixel boundaries at different moments while
        // panning, so they shift by a pixel against each other — the map appears to shiver
        // rather than slide. One grid plus one shared offset keeps them locked together and the
        // motion smooth.
        g.pose().pushPose();
        float panX = camera.panX();
        float panY = camera.panY();
        g.pose().translate(panX - (float) Math.floor(panX), panY - (float) Math.floor(panY), 0f);

        switch (bg.mode()) {
            case "GRID" -> drawGrid(g);
            case "TEXTURE" -> drawBackgroundTexture(g, bg.texture());
            default -> { /* SOLID: the flat fill drawn above is the whole background */ }
        }

        if (model != null) {
            String hoverKey = camera.within(mouseX, mouseY) ? nodeAt(mouseX, mouseY) : null;
            float hover = animationsEnabled()
                    ? hoverAnim.ramp(hoverKey != null, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS)
                    : (hoverKey != null ? 1f : 0f);

            // Edges first so their lines pass behind the nodes.
            for (StageGraphModel.Edge edge : model.edges()) {
                if (edgeOffScreen(edge)) continue;
                drawEdge(g, edge);
            }
            for (Map.Entry<String, StageGraphModel.Node> e : drawOrder) {
                if (nodeOffScreen(e.getValue())) continue;
                drawNodeShape(g, e.getValue(), e.getKey().equals(hoverKey) ? hover : 0f);
            }
            if (camera.zoom() >= LABEL_HIDE_ZOOM) {
                for (Map.Entry<String, StageGraphModel.Node> e : drawOrder) {
                    if (nodeOffScreen(e.getValue())) continue;
                    drawLabel(g, font, e.getValue());
                }
            }
            if (focusedKey != null) {
                StageGraphModel.Node focused = model.nodes().get(focusedKey);
                if (focused != null) drawFocusHighlight(g, focused);
            }
            for (String key : selected) {
                if (key.equals(focusedKey)) continue;
                StageGraphModel.Node node = model.nodes().get(key);
                if (node != null && !nodeOffScreen(node)) drawFocusHighlight(g, node);
            }
            overlay.run();
        }

        g.pose().popPose();
        g.disableScissor();
    }

    /** Drawn radius of {@code node} at the current zoom, before hover lift. */
    int nodeRadiusFor(StageGraphModel.Node node) {
        return nodeRadius(StageGraphConfig.styleFor(node.stageId(), node.individual(), node.state()));
    }

    private static boolean animationsEnabled() {
        return GraphConfig.GRAPH.animations.get();
    }

    /**
     * Tiles the background texture across the viewport, panning and zooming with the
     * graph exactly as the grid does — a background that stayed still while the map moved would
     * read as a window rather than as ground.
     *
     * <p>One quad with a repeating UV range, not one blit per tile. {@code GuiGraphics.blit} is
     * not batched: every call is its own draw, so a screenful of tiles cost hundreds of draws a
     * frame and made the whole graph, sidebar included, feel sluggish. This is a single draw at
     * any zoom, with no loop to bound.
     *
     * <p>One tile covers one grid cell, so the texture lines up with the grid instead of drifting
     * against it. Anything unusable — blank, unparseable, or a texture the pack does not ship —
     * leaves the plain fill that is already on screen; a missing-texture chequerboard behind the
     * whole graph would be far worse than a colour.
     */
    private void drawBackgroundTexture(GuiGraphics g, String texture) {
        ResourceLocation tex = ResourceLocation.tryParse(texture);
        if (tex == null) return;

        float tile = camera.cellSize() * camera.zoom();
        if (tile < 1.0f) return; // below a pixel per tile there is nothing left to see

        // Floored like screenX/screenY: the fractional part of the pan is supplied by the pose
        // this runs inside, and counting it twice would make the texture crawl under the grid.
        float originX = (int) Math.floor(camera.panX());
        float originY = (int) Math.floor(camera.panY());

        float u1 = (camera.viewX() - originX) / tile;
        float v1 = (camera.viewY() - originY) / tile;
        NodeTextures.repeatingQuad(g, tex, camera.viewX(), camera.viewY(), camera.viewX() + camera.viewW(), camera.viewY() + camera.viewH(),
                u1, v1, u1 + camera.viewW() / tile, v1 + camera.viewH() / tile);
    }

    private void drawGrid(GuiGraphics g) {
        double cellSpacing = (double) camera.cellSize() * camera.zoom();
        if (cellSpacing <= 0.0) return;

        // Coarsen instead of shrink. Every line is one pixel wide, so zooming out does not make
        // them thicker — it packs them closer, and below roughly a finger's width apart the grid
        // stops reading as a reference and starts reading as a flat texture over the whole
        // canvas. Doubling the step keeps the drawn spacing inside a comfortable band at every
        // zoom, so the grid stays a grid rather than fading out or turning into a mesh.
        int step = 1;
        while (cellSpacing * step < MIN_GRID_SPACING) step *= 2;
        double spacing = cellSpacing * step;

        int gridColor = GRID_COLOR;

        // Both loops are bounded by a line count computed up front, and step in double rather
        // than float. The earlier version was an unbounded for(;;) that exited only once a
        // float sum exceeded the viewport — and a float at the magnitude pan reaches after a
        // few zoom-toward-cursor steps has a spacing larger than the increment, so the sum
        // stops advancing and the loop never ends. That hung the render thread.
        int maxLines = (int) Math.ceil(Math.max(camera.viewW(), camera.viewH()) / spacing) + 3;

        // Lines are placed with screenX/screenY — the same transform the nodes use — so a grid
        // line always sits exactly on the cell a node occupies. Computing them independently
        // meant a second rounding of the same value, and the two drifted against each other by
        // up to a pixel while panning, which reads as the grid crawling under the graph.
        // Snapped down to a multiple of the step so the drawn lines stay on the same cells as the
        // zoom changes — otherwise which lines survive the coarsening shifts while panning.
        int firstCol = Math.floorDiv(camera.gridX(camera.viewX()) - step, step) * step;
        for (int i = 0; i <= maxLines; i++) {
            int x = camera.screenX(firstCol + i * step);
            if (x > camera.viewX() + camera.viewW()) break;
            if (x >= camera.viewX()) g.fill(x, camera.viewY(), x + 1, camera.viewY() + camera.viewH(), gridColor);
        }

        int firstRow = Math.floorDiv(camera.gridY(camera.viewY()) - step, step) * step;
        for (int i = 0; i <= maxLines; i++) {
            int y = camera.screenY(firstRow + i * step);
            if (y > camera.viewY() + camera.viewH()) break;
            if (y >= camera.viewY()) g.fill(camera.viewX(), y, camera.viewX() + camera.viewW(), y + 1, gridColor);
        }
    }

    // --- Nodes ----------------------------------------------------------------------------------

    private int nodeRadius(ResolvedStyle style) {
        return Math.max(3, Math.round(BASE_NODE_RADIUS * (float) style.size() * camera.zoom()));
    }


    private void drawNodeShape(GuiGraphics g, StageGraphModel.Node node, float hoverT) {
        ResolvedStyle style = StageGraphConfig.styleFor(node.stageId(), node.individual(), node.state());
        int cx = camera.screenX(node.pos().x());
        int cy = camera.screenY(node.pos().y());
        int r = nodeRadius(style);

        float scale = 1f + HOVER_LIFT * Ease.outCubic(hoverT);
        boolean scaled = Math.abs(scale - 1f) > 1.0e-3f;
        if (scaled) {
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            g.pose().scale(scale, scale, 1f);
            g.pose().translate(-cx, -cy, 0);
        }

        int bw = Math.max(0, Math.round(style.borderWidth() * camera.zoom()));
        NodeShapes.draw(g, style.shape(), cx, cy, r, style.fillArgb(), style.border(), bw);
        drawNodeIcon(g, node, cx, cy, r);
        if (style.checkmark()) {
            NodeShapes.statusBadge(g, node.state(), cx, cy, r, style.border());
        }

        if (scaled) g.pose().popPose();
    }

    /**
     * The stage's {@code icon} item, drawn inside the node.
     *
     * <p>Sized to 1.25× the node radius, which still fits inside a diamond — its inscribed square
     * has a side of about 1.41× the radius, and the diamond is the tightest of the five shapes.
     * Below a few pixels the sprite is a smudge rather than a symbol, so it is dropped instead.
     */
    private void drawNodeIcon(GuiGraphics g, StageGraphModel.Node node, int cx, int cy, int r) {
        if (node.icon() == null || node.icon().isEmpty()) return;
        ItemStack stack = iconStack(node.icon());
        if (stack.isEmpty()) return;

        float side = r * 1.25f;
        if (side < 6f) return;

        g.pose().pushPose();
        g.pose().translate(cx - side / 2f, cy - side / 2f, 0);
        g.pose().scale(side / 16f, side / 16f, 1f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    /** Cached: this runs once per visible node per frame, and a registry lookup is not free. */
    private ItemStack iconStack(String id) {
        return iconCache.computeIfAbsent(id, key -> {
            ResourceLocation rl = ResourceLocation.tryParse(key);
            if (rl == null) return ItemStack.EMPTY;
            Item item = BuiltInRegistries.ITEM.get(rl);
            // An unknown id resolves to AIR rather than null, so both cases land here.
            return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        });
    }

    private void drawLabel(GuiGraphics g, Font font, StageGraphModel.Node node) {
        ResolvedStyle style = StageGraphConfig.styleFor(node.stageId(), node.individual(), node.state());
        if ("NONE".equals(style.label())) return;
        String text = "ID".equals(style.label()) ? node.stageId() : node.label();
        if (text == null || text.isEmpty()) return;

        int cx = camera.screenX(node.pos().x());
        int topY = camera.screenY(node.pos().y()) + nodeRadius(style) + 2;
        g.pose().pushPose();
        g.pose().translate(cx, topY, 0);
        g.pose().scale(camera.zoom(), camera.zoom(), 1f);
        int tw = font.width(text);
        g.drawString(font, text, -tw / 2, 0, style.labelColor(), false);
        g.pose().popPose();
    }

    private void drawFocusHighlight(GuiGraphics g, StageGraphModel.Node node) {
        ResolvedStyle style = StageGraphConfig.styleFor(node.stageId(), node.individual(), node.state());
        int cx = camera.screenX(node.pos().x());
        int cy = camera.screenY(node.pos().y());
        int r = nodeRadius(style);
        int ringW = Math.max(2, Math.round(3f * camera.zoom()));
        NodeShapes.draw(g, style.shape(), cx, cy, r + ringW, FOCUS_RING_COLOR, FOCUS_RING_COLOR, ringW);
    }

    // --- Edges ----------------------------------------------------------------------------------

    /** A drawable path in screen space: {@code xs[i], ys[i]} for i in [0, xs.length). */
    private record Path(float[] xs, float[] ys) {}

    private record Point(float x, float y) {}

    private void drawEdge(GuiGraphics g, StageGraphModel.Edge edge) {
        StageGraphModel.Node from = model.nodes().get(edge.fromKey());
        StageGraphModel.Node to = model.nodes().get(edge.toKey());
        if (from == null || to == null) return; // StageGraphModel guarantees both exist; defensive only

        ResolvedStyle fromStyle = StageGraphConfig.styleFor(from.stageId(), from.individual(), from.state());
        ResolvedStyle toStyle = StageGraphConfig.styleFor(to.stageId(), to.individual(), to.state());

        float fx = camera.screenX(from.pos().x());
        float fy = camera.screenY(from.pos().y());
        float tx = camera.screenX(to.pos().x());
        float ty = camera.screenY(to.pos().y());

        GraphConfig.Graph cfg = GraphConfig.GRAPH;
        Path path = buildPath(fx, fy, tx, ty, cfg.edgeRouting.get());
        float[] xs = path.xs();
        float[] ys = path.ys();
        int last = xs.length - 1;

        // Inset the endpoints to the node boundary so the line (and its arrowhead) don't start
        // or end underneath the node shape.
        Point p0 = insetPoint(xs[0], ys[0], xs[1], ys[1], nodeRadius(fromStyle));
        Point pn = insetPoint(xs[last], ys[last], xs[last - 1], ys[last - 1], nodeRadius(toStyle));
        xs[0] = p0.x();
        ys[0] = p0.y();
        xs[last] = pn.x();
        ys[last] = pn.y();

        // An OR-group edge always uses orGroupStyle, regardless of state, to read as "one of
        // several alternatives" — the met/open colour still communicates its actual state.
        GraphConfig.EdgeStyle styleEnum = edge.orGroup() ? cfg.orGroupStyle.get()
                : (edge.satisfied() ? cfg.edgeStyleMet.get() : cfg.edgeStyleOpen.get());
        int color = 0xFF000000 | ResolvedStyle.parseColor(
                edge.satisfied() ? cfg.edgeColorMet.get() : cfg.edgeColorOpen.get(), 0x999999);
        // Floor of one pixel, not 1.5 — anything higher stops the line shrinking with the rest of
        // the canvas well before the minimum zoom is reached.
        float thickness = Math.max(1.0f, cfg.edgeWidth.get() * camera.zoom());

        // Two independent axes: the line style says AND vs OR, the colour says satisfied vs open.
        // With the stock config that is unambiguous — AND is solid either way, so dashed means OR.
        //
        // A pack author can still set styleOpen to DASHED, at which point an open AND edge and an
        // OR edge would look the same. Rather than let them collide, OR falls back to a distinctly
        // finer dotted pattern in that case only; at default settings it gets the ordinary dash,
        // which is far easier to read than dots.
        boolean openAndAlsoDashed = cfg.edgeStyleOpen.get() == GraphConfig.EdgeStyle.DASHED;
        boolean needsFinerPattern = edge.orGroup() && openAndAlsoDashed;

        float dashLen = 0f;
        float gapLen = 0f;
        if (styleEnum == GraphConfig.EdgeStyle.DASHED) {
            if (needsFinerPattern) {
                dashLen = Math.max(2f, 2.5f * camera.zoom());
                gapLen = Math.max(2f, 3.5f * camera.zoom());
            } else {
                dashLen = Math.max(3f, 7f * camera.zoom());
                gapLen = Math.max(2f, 5f * camera.zoom());
            }
        }

        drawPath(g, xs, ys, color, thickness, dashLen, gapLen);
        if (cfg.edgeArrowheads.get()) {
            drawArrowHead(g, xs[last], ys[last], xs[last - 1], ys[last - 1], thickness, color);
        }
    }

    private Path buildPath(float fx, float fy, float tx, float ty, GraphConfig.EdgeRouting routing) {
        return switch (routing) {
            // Two horizontal legs joined by one vertical leg at the x-midpoint — the standard
            // "elbow" connector look, and what lets the path reach an arbitrary (tx,ty) while
            // only ever running horizontal or vertical.
            case ORTHOGONAL -> {
                float midX = (fx + tx) / 2f;
                yield new Path(new float[]{fx, midX, midX, tx}, new float[]{fy, fy, ty, ty});
            }
            case CURVED -> buildBezier(fx, fy, tx, ty);
            default -> new Path(new float[]{fx, tx}, new float[]{fy, ty});
        };
    }

    /** Cubic Bezier with control points offset horizontally by half the source-target span. */
    private Path buildBezier(float fx, float fy, float tx, float ty) {
        float span = (tx - fx) / 2f;
        float c1x = fx + span, c1y = fy;
        float c2x = tx - span, c2y = ty;
        float[] xs = new float[BEZIER_SEGMENTS + 1];
        float[] ys = new float[BEZIER_SEGMENTS + 1];
        for (int i = 0; i <= BEZIER_SEGMENTS; i++) {
            float t = (float) i / BEZIER_SEGMENTS;
            xs[i] = cubic(fx, c1x, c2x, tx, t);
            ys[i] = cubic(fy, c1y, c2y, ty, t);
        }
        return new Path(xs, ys);
    }

    private static float cubic(float p0, float p1, float p2, float p3, float t) {
        float u = 1f - t;
        return u * u * u * p0 + 3f * u * u * t * p1 + 3f * u * t * t * p2 + t * t * t * p3;
    }

    private static Point insetPoint(float fromX, float fromY, float towardX, float towardY, float radius) {
        float dx = towardX - fromX, dy = towardY - fromY;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1f) return new Point(fromX, fromY);
        return new Point(fromX + dx / len * radius, fromY + dy / len * radius);
    }

    /**
     * Draws every segment of a multi-point path, solid or dashed.
     *
     * <p>Works in line <em>height</em> rather than radius, so a single-pixel hairline is
     * expressible. Going through a radius forced a minimum height of two pixels, and since
     * everything else on the canvas keeps shrinking with the zoom, that floor is what made the
     * lines look heavier the further out you went — they were the only thing that stopped
     * getting smaller.
     */
    private void drawPath(GuiGraphics g, float[] xs, float[] ys, int color, float thickness,
                          float dashLen, float gapLen) {
        // Thickness stays a float all the way to the vertex buffer. Rounding it to whole pixels is
        // what forced a two-pixel floor before: an integer-sized rotated rectangle either snaps up
        // to two pixels or, at one, misses pixel centres along a diagonal and breaks into specks.
        // A quad with sub-pixel corners and a filtered texture just gets fainter as it thins, the
        // way FTB Quests' dependency lines do.
        float h = Math.max(MIN_LINE_H, thickness);
        if (dashLen > 0f) {
            drawDashedPolyline(g, xs, ys, color, h, dashLen, gapLen);
            return;
        }
        // Joins only once the line is thick enough for the notch at a bend to be worth patching.
        // A curve is sampled into many short segments, so each bend turns by a couple of degrees
        // and the notch is well under a pixel on a thin line — while the dot covering it is a
        // circle texture squeezed into a few pixels, more conspicuous than the seam.
        int joinRadius = h >= 3f ? Math.round(h / 2f) : 0;
        for (int i = 0; i < xs.length - 1; i++) {
            drawSegment(g, xs[i], ys[i], xs[i + 1], ys[i + 1], h, color);
            if (i > 0 && joinRadius > 0) blitDot(g, xs[i], ys[i], joinRadius, color);
        }
        if (joinRadius > 0) {
            blitDot(g, xs[0], ys[0], joinRadius, color);
            blitDot(g, xs[xs.length - 1], ys[xs.length - 1], joinRadius, color);
        }
    }

    /**
     * Walks the whole path with a dash phase that carries across segments, so a CURVED edge's
     * many small sub-segments read as one continuous dashed line rather than restarting the
     * pattern at every sample point.
     */
    private void drawDashedPolyline(GuiGraphics g, float[] xs, float[] ys, int color, float h,
                                    float dash, float gap) {
        float cycle = dash + gap;
        float carried = 0f;

        for (int i = 0; i < xs.length - 1; i++) {
            float x1 = xs[i], y1 = ys[i], x2 = xs[i + 1], y2 = ys[i + 1];
            float dx = x2 - x1, dy = y2 - y1;
            float len = (float) Math.hypot(dx, dy);
            if (len < 0.5f) continue;
            float angle = (float) Math.atan2(dy, dx);

            g.pose().pushPose();
            g.pose().translate(x1, y1, 0);
            g.pose().mulPose(Axis.ZP.rotation(angle));

            // Stepped by dash index, not by an accumulating cursor. The previous version advanced
            // a float `pos` by a computed remainder; that remainder can be arbitrarily small, and
            // once the segment is long enough that the remainder falls below the float spacing at
            // `pos`, `pos` stops advancing and the loop never terminates. Segment length scales
            // directly with zoom, so this was reachable simply by zooming in — and it hit dashed
            // edges, which is every unsatisfied dependency and every OR group.
            float phase = carried % cycle;
            float firstStart = phase < dash ? -phase : cycle - phase;
            int maxDashes = (int) Math.ceil((len + cycle) / cycle) + 1;

            for (int k = 0; k < maxDashes; k++) {
                float start = firstStart + k * cycle;
                if (start > len) break;
                float from = Math.max(0f, start);
                float to = Math.min(len, start + dash);
                if (to > from) blitLine(g, from, to, h, color);
            }

            g.pose().popPose();
            carried += len;
        }
    }

    /** Draws one straight segment as a rotated quad; returns its length. */
    private float drawSegment(GuiGraphics g, float x1, float y1, float x2, float y2,
                              float h, int color) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        if (len < 0.5f) return 0f;
        float angle = (float) Math.atan2(dy, dx);
        g.pose().pushPose();
        g.pose().translate(x1, y1, 0);
        g.pose().mulPose(Axis.ZP.rotation(angle));
        blitLine(g, 0f, len, h, color);
        g.pose().popPose();
        return len;
    }

    /**
     * Draws a shaft from local x = {@code s} to x = {@code e}, {@code h} pixels tall and centred
     * on the local x axis. Called inside a rotated pose, so this is a rotated quad.
     *
     * <p>Everything here is a float, right through to the vertex buffer. Both {@code fill} and
     * {@code blit} take integers, which rounds the thickness to whole pixels <em>before</em> the
     * pose rotates it — a 1.4 pixel line at an angle simply cannot be expressed, so it either
     * snaps to two pixels or falls apart. The line texture carries the soft border rows that make
     * the edge fade instead of stair-stepping, and it is filtered linearly for the same reason.
     */
    private void blitLine(GuiGraphics g, float s, float e, float h, int color) {
        if (e <= s || h <= 0f) return;
        NodeTextures.quad(g, NodeTextures.line(), s, -h / 2f, e, h / 2f, color);
    }

    private void blitDot(GuiGraphics g, float x, float y, int r, int color) {
        NodeTextures.blit(g, NodeTextures.circle(), Math.round(x) - r, Math.round(y) - r, r * 2, r * 2,
                NodeTextures.SIZE, NodeTextures.SIZE, color);
    }

    /** Draws the arrowhead at (tx,ty), pointing from (fromX,fromY) toward it. */
    private void drawArrowHead(GuiGraphics g, float tx, float ty, float fromX, float fromY,
                               float thickness, int color) {
        float angle = (float) Math.atan2(ty - fromY, tx - fromX);
        // Tied to the line it terminates. The old floor of four pixels left an eight-pixel head on
        // a one-pixel line at low zoom, next to nodes barely ten pixels across.
        int ah = Math.max(2, Math.round(2.2f * thickness));
        g.pose().pushPose();
        g.pose().translate(tx, ty, 0);
        g.pose().mulPose(Axis.ZP.rotation(angle));
        NodeTextures.blit(g, NodeTextures.arrow(), -2 * ah, -ah, 2 * ah, 2 * ah,
                NodeTextures.SIZE, NodeTextures.SIZE, color);
        g.pose().popPose();
    }

    /**
     * Returns the graph key of the topmost node under (mx,my), or null. Iterates in reverse
     * draw order so a node placed on top of another (hand-placement makes this routine once
     * nodes are dragged) wins the hit rather than whichever happened to be inserted first.
     */
    String nodeAt(double mx, double my) {
        if (model == null || !camera.within(mx, my)) return null;
        for (int i = drawOrder.size() - 1; i >= 0; i--) {
            Map.Entry<String, StageGraphModel.Node> e = drawOrder.get(i);
            StageGraphModel.Node node = e.getValue();
            ResolvedStyle style = StageGraphConfig.styleFor(node.stageId(), node.individual(), node.state());
            int cx = camera.screenX(node.pos().x());
            int cy = camera.screenY(node.pos().y());
            int r = nodeRadius(style) + HIT_PADDING;
            if (Math.abs(mx - cx) <= r && Math.abs(my - cy) <= r) {
                return e.getKey();
            }
        }
        return null;
    }

    // --- Culling ----------------------------------------------------------------------------
    //
    // Zooming in puts most of a large pack outside the window, and everything outside it was
    // still being shaped, blitted and text-measured before the scissor threw it away. These two
    // checks are deliberately arithmetic only — no style lookup, no allocation — because a cull
    // test that costs as much as the draw it skips is not worth having.

    /**
     * Half-extent generous enough to cover the largest a node can possibly draw: the biggest
     * {@code size} the config permits, at full hover lift, plus room for the label underneath.
     * Erring large only means drawing something just off screen; erring small pops nodes out of
     * existence at the edge, which is far more noticeable.
     */
    private float cullMargin() {
        return BASE_NODE_RADIUS * MAX_STYLE_SIZE * camera.zoom() * (1f + HOVER_LIFT) + LABEL_CULL_PAD;
    }

    private boolean nodeOffScreen(StageGraphModel.Node node) {
        float m = cullMargin();
        int x = camera.screenX(node.pos().x());
        int y = camera.screenY(node.pos().y());
        return x + m < camera.viewX() || x - m > camera.viewX() + camera.viewW()
                || y + m < camera.viewY() || y - m > camera.viewY() + camera.viewH();
    }

    private boolean edgeOffScreen(StageGraphModel.Edge edge) {
        StageGraphModel.Node a = model.nodes().get(edge.fromKey());
        StageGraphModel.Node b = model.nodes().get(edge.toKey());
        if (a == null || b == null) return true;

        int x1 = camera.screenX(a.pos().x()), y1 = camera.screenY(a.pos().y());
        int x2 = camera.screenX(b.pos().x()), y2 = camera.screenY(b.pos().y());
        // CURVED routing bows out from the straight line by up to half the horizontal span, so
        // the box cannot simply hug the endpoints or long curves would vanish while still visible.
        float bow = Math.abs(x2 - x1) * 0.5f + cullMargin();

        return Math.max(x1, x2) + bow < camera.viewX() || Math.min(x1, x2) - bow > camera.viewX() + camera.viewW()
                || Math.max(y1, y2) + bow < camera.viewY() || Math.min(y1, y2) - bow > camera.viewY() + camera.viewH();
    }


}
