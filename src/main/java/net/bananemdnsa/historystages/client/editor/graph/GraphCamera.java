package net.bananemdnsa.historystages.client.editor.graph;

import net.bananemdnsa.historystages.GraphConfig;
import net.bananemdnsa.historystages.data.graph.GraphPos;

/**
 * The graph's camera: the viewport, where grid cell (0,0) sits on screen and how large a cell is.
 *
 * <p>The grid-cell -> screen-pixel transform ({@link #screenX}/{@link #screenY}, and their inverses
 * {@link #gridX}/{@link #gridY}) is defined exactly once here and used by every drawing and
 * hit-testing method. Nothing re-derives it inline — that was the bug in the screen this replaced,
 * where the same formula was copied at four separate sites and drifted.
 */
final class GraphCamera {

    /** Scroll wheel notches per 1.0 of zoom. */
    private static final float ZOOM_STEP = 0.1f;

    /** See {@link #setClipRight}. Unbounded until the screen says otherwise. */
    private int clipRight = Integer.MAX_VALUE;

    // Viewport, in screen pixels — the area this canvas draws into and clips to.
    private int viewX, viewY, viewW, viewH;

    // Camera. panX/panY is the screen position of grid cell (0,0) at the current zoom.
    private float panX;
    private float panY;
    private float zoom;

    GraphCamera(int x, int y, int width, int height) {
        setBounds(x, y, width, height);
        this.zoom = (float) (double) GraphConfig.GRAPH.startZoom.get();
        this.panX = x + width / 2f;
        this.panY = y + height / 2f;
    }

    void setBounds(int x, int y, int width, int height) {
        this.viewX = x;
        this.viewY = y;
        this.viewW = width;
        this.viewH = height;
        this.clipRight = Integer.MAX_VALUE;
    }

    int viewX() { return viewX; }
    int viewY() { return viewY; }
    int viewW() { return viewW; }
    int viewH() { return viewH; }
    float panX() { return panX; }
    float panY() { return panY; }
    float zoom() { return zoom; }
    int clipRight() { return clipRight; }

    /** Moves the map by a mouse delta, kept within reach of the viewport. */
    void pan(double dx, double dy, StageGraphModel model) {
        panX += (float) dx;
        panY += (float) dy;
        clampPan(model);
    }

    /** Centres the viewport on a grid cell at the current zoom. */
    void centerOn(GraphPos pos) {
        panX = viewX + viewW / 2f - pos.x() * cellSize() * zoom;
        panY = viewY + viewH / 2f - pos.y() * cellSize() * zoom;
    }

    /**
     * Right edge the canvas is allowed to draw up to, independent of its bounds.
     *
     * <p>The detail panel is an overlay: the canvas keeps its full width so opening the panel
     * never shifts the camera. But item icons are rendered by Minecraft on its own, higher z
     * layer, so anything drawn underneath the panel punches straight through it. Clipping the
     * canvas at the panel's edge stops it being drawn there in the first place, which is both
     * correct and cheaper than fighting the z order.
     */
    void setClipRight(int rightEdge) {
        this.clipRight = rightEdge;
    }

    // --- Transform: the single place grid space and screen space meet ------------------------

    /** Grid cell -> screen pixel. */
    int screenX(int gridX) {
        return (int) Math.floor(panX) + Math.round(gridX * cellSize() * zoom);
    }

    int screenY(int gridY) {
        return (int) Math.floor(panY) + Math.round(gridY * cellSize() * zoom);
    }

    /** Screen pixel -> grid cell, rounded to the nearest cell. */
    int gridX(double screenX) {
        return Math.round((float) ((screenX - panX) / (cellSize() * zoom)));
    }

    int gridY(double screenY) {
        return Math.round((float) ((screenY - panY) / (cellSize() * zoom)));
    }

    int cellSize() {
        return GraphConfig.GRAPH.gridSize.get();
    }

    boolean within(double mx, double my) {
        return mx >= viewX && mx < viewX + viewW && my >= viewY && my < viewY + viewH;
    }

    /**
     * Keeps the map within reach of the viewport.
     *
     * <p>Two reasons. The obvious one is that scrolling the graph off into nowhere and having no
     * way back is a bad time. The less obvious one is that it bounds how large {@code panX} and
     * {@code panY} can grow: {@code GraphRenderer.drawGrid} steps along the viewport in units of the grid
     * spacing, and once the camera offset reaches a magnitude where that step disappears into
     * rounding, the walk stops advancing. The loop there is bounded now regardless, but keeping
     * the camera near the content removes the condition rather than only surviving it.
     */
    void clampPan(StageGraphModel model) {
        if (model == null || model.nodes().isEmpty()) return;

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (StageGraphModel.Node node : model.nodes().values()) {
            minX = Math.min(minX, node.pos().x());
            maxX = Math.max(maxX, node.pos().x());
            minY = Math.min(minY, node.pos().y());
            maxY = Math.max(maxY, node.pos().y());
        }

        double cell = (double) cellSize() * zoom;
        // At least this much of the content must stay inside the viewport.
        double margin = Math.max(48.0, Math.min(viewW, viewH) * 0.4);

        panX = (float) clampBetween(panX,
                viewX + margin - maxX * cell, viewX + viewW - margin - minX * cell);
        panY = (float) clampBetween(panY,
                viewY + margin - maxY * cell, viewY + viewH - margin - minY * cell);
    }

    /**
     * Drops the camera's sub-pixel remainder once the movement that produced it has finished.
     *
     * <p>That remainder is what keeps panning smooth, but it is applied as a pose translation, and
     * text rendered at a fractional offset is slightly soft. Nobody is reading labels mid-drag;
     * they read them once the map is standing still. So the fraction exists exactly as long as
     * something is moving, and the view settles on whole pixels.
     */
    void snapCameraToPixel() {
        panX = Math.round(panX);
        panY = Math.round(panY);
    }

    /** Clamps without assuming which bound is the lower one — at high zoom they can swap. */
    private static double clampBetween(double value, double a, double b) {
        double lo = Math.min(a, b);
        double hi = Math.max(a, b);
        return value < lo ? lo : Math.min(value, hi);
    }

    /** Fits the viewport to the bounding box of every currently visible node. */
    void fitToContent(StageGraphModel model) {
        double minZoomCfg = GraphConfig.GRAPH.minZoom.get();
        double maxZoomCfg = GraphConfig.GRAPH.maxZoom.get();

        if (model == null || model.nodes().isEmpty()) {
            zoom = (float) Math.max(minZoomCfg, Math.min(maxZoomCfg, GraphConfig.GRAPH.startZoom.get()));
            panX = viewX + viewW / 2f;
            panY = viewY + viewH / 2f;
            return;
        }

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (StageGraphModel.Node node : model.nodes().values()) {
            minX = Math.min(minX, node.pos().x());
            maxX = Math.max(maxX, node.pos().x());
            minY = Math.min(minY, node.pos().y());
            maxY = Math.max(maxY, node.pos().y());
        }

        int cell = cellSize();
        // +1 cell of padding on top of the column/row span so edge nodes and their captions
        // are not flush against the viewport border.
        float contentW = (maxX - minX) * cell + cell;
        float contentH = (maxY - minY) * cell + cell;

        float fitZoom = Math.min(viewW / contentW, viewH / contentH);
        zoom = (float) Math.max(minZoomCfg, Math.min(maxZoomCfg, fitZoom));

        float centerGridX = (minX + maxX) / 2f;
        float centerGridY = (minY + maxY) / 2f;
        panX = viewX + viewW / 2f - centerGridX * cell * zoom;
        panY = viewY + viewH / 2f - centerGridY * cell * zoom;
    }

    boolean scroll(double mx, double my, double scrollY, StageGraphModel model) {
        if (!within(mx, my)) return false;
        zoomAround(mx, my, zoom + scrollY * ZOOM_STEP, model);
        return true;
    }

    /** Keyboard zoom: the same step as one wheel notch, around the middle of the view. */
    void zoomBy(int steps, StageGraphModel model) {
        zoomAround(viewX + viewW / 2.0, viewY + viewH / 2.0, zoom + steps * ZOOM_STEP, model);
    }

    /** Back to the configured start zoom, keeping whatever is in the middle of the view there. */
    void resetZoom(StageGraphModel model) {
        zoomAround(viewX + viewW / 2.0, viewY + viewH / 2.0, GraphConfig.GRAPH.startZoom.get(), model);
    }

    /** Keeps the grid point under (mx, my) fixed on screen while the zoom changes. */
    private void zoomAround(double mx, double my, double target, StageGraphModel model) {
        float old = zoom;
        double minZoomCfg = GraphConfig.GRAPH.minZoom.get();
        double maxZoomCfg = GraphConfig.GRAPH.maxZoom.get();
        zoom = (float) Math.max(minZoomCfg, Math.min(maxZoomCfg, target));
        if (zoom == old) return;   // already at a limit, leave the camera alone
        panX = (float) (mx - ((mx - panX) / old) * zoom);
        panY = (float) (my - ((my - panY) / old) * zoom);
        clampPan(model);
        snapCameraToPixel();
    }

}
