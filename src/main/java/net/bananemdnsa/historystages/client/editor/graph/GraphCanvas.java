package net.bananemdnsa.historystages.client.editor.graph;

import net.bananemdnsa.historystages.data.graph.GraphPos;
import net.bananemdnsa.historystages.data.graph.ResolvedCanvasBackground;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * The stage graph's drawing surface as the screen sees it. Owns no application state beyond the
 * currently focused node — the sidebar, the detail panel and everything they trigger belong to the
 * screen that composes this canvas.
 *
 * <p>The work is split three ways: {@link GraphCamera} (viewport, pan, zoom and the one
 * grid-to-screen transform), {@link GraphRenderer} (everything drawn on the map) and
 * {@link GraphInteraction} (panning, dragging, dropping). This class only wires them together and
 * keeps the public surface the screen and the sidebar use.
 */
public final class GraphCanvas {

    /**
     * Reports a completed drag-drop to the host screen, which alone decides whether a touched
     * tree needs a freeze confirmation first. The move itself is not applied to the model until
     * {@code commit} runs — declining the confirmation leaves the canvas exactly as it was
     * before the drag, since nothing was ever mutated.
     */
    public interface DragHandler {
        /**
         * @param resultingPositions one entry per tree the drop touches (individual flag → that
         *                           tree's complete position map); a group drag can touch both
         */
        void onDrop(Map<Boolean, Map<String, GraphPos>> resultingPositions, Runnable commit);
    }

    /**
     * Where a node dragged off the map can be dropped to take it out of the graph — the sidebar.
     * Asked on release, so the canvas itself never needs to know where the sidebar is.
     */
    public interface RemoveTarget {
        boolean contains(double mx, double my);

        void onRemove(Set<String> graphKeys);
    }

    private final GraphCamera camera;
    private final GraphRenderer renderer;
    private final GraphInteraction interaction;

    private StageGraphModel model;
    /** Graph key of the node the highlight ring is drawn around, set by {@link #focusOn}. */
    private String focusedKey;
    private ResolvedCanvasBackground background;

    public GraphCanvas(StageGraphModel model, int x, int y, int width, int height, boolean editor) {
        this.camera = new GraphCamera(x, y, width, height);
        this.renderer = new GraphRenderer(camera);
        this.interaction = new GraphInteraction(camera, renderer, editor, renderer::refreshDrawOrder);
        setModel(model);
    }

    public void setDragHandler(DragHandler handler) {
        interaction.setDragHandler(handler);
    }

    public void setRemoveTarget(RemoveTarget target) {
        interaction.setRemoveTarget(target);
    }

    /** Graph keys picked with Ctrl-click or the rubber band. */
    public Set<String> selection() {
        return interaction.selection();
    }

    public void select(Collection<String> graphKeys) {
        interaction.select(graphKeys);
    }

    public void clearSelection() {
        interaction.clearSelection();
    }

    public void selectAll() {
        interaction.selectAll();
    }

    /** True while a pan, drag or rubber band is in progress; keyboard shortcuts wait for it. */
    public boolean isBusy() {
        return interaction.isBusy();
    }

    public void zoomBy(int steps) {
        camera.zoomBy(steps, model);
    }

    public void resetZoom() {
        camera.resetZoom(model);
    }

    /** Null draws graph.toml's background; the player view hands in its own. */
    public void setBackground(ResolvedCanvasBackground background) {
        this.background = background;
    }

    public void setModel(StageGraphModel model) {
        this.model = model;
        renderer.setModel(model);
        interaction.setModel(model);
    }

    public void setBounds(int x, int y, int width, int height) {
        camera.setBounds(x, y, width, height);
    }

    /** See {@link GraphCamera#setClipRight}. */
    public void setClipRight(int rightEdge) {
        camera.setClipRight(rightEdge);
    }

    /** The node currently highlighted (last target of {@link #focusOn}), or null. */
    public String focusedKey() {
        return focusedKey;
    }

    public int screenX(int gridX) {
        return camera.screenX(gridX);
    }

    public int screenY(int gridY) {
        return camera.screenY(gridY);
    }

    public int gridX(double screenX) {
        return camera.gridX(screenX);
    }

    public int gridY(double screenY) {
        return camera.gridY(screenY);
    }

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY, float partialTick) {
        if (camera.viewW() <= 0 || camera.viewH() <= 0) return;
        ResolvedCanvasBackground bg = background != null ? background : CanvasBackgrounds.fromConfig();
        renderer.renderMap(g, font, bg, mouseX, mouseY, focusedKey, interaction.selection(),
                () -> interaction.renderInMap(g, mouseX, mouseY));
        interaction.renderOverlay(g, font, mouseX, mouseY);
    }

    /** Graph key of the topmost node under the point, or null. */
    public String nodeAt(double mx, double my) {
        return renderer.nodeAt(mx, my);
    }

    /**
     * Highlights {@code graphKey} without moving the camera.
     *
     * <p>Selecting a node by clicking it must not recentre the view: the node is already under
     * the cursor, and yanking the map out from under a click makes the canvas feel like it is
     * fighting you. Recentring is what {@link #focusOn} is for, and that is the sidebar's job —
     * there the target genuinely may be off screen.
     */
    public void highlight(String graphKey) {
        focusedKey = graphKey;
    }

    /** Centres the viewport on {@code graphKey} at the current zoom, and highlights it. */
    public void focusOn(String graphKey) {
        if (model == null) return;
        StageGraphModel.Node node = model.nodes().get(graphKey);
        if (node == null) return;
        focusedKey = graphKey;
        camera.centerOn(node.pos());
    }

    /** Fits the viewport to the bounding box of every currently visible node. */
    public void fitToContent() {
        camera.fitToContent(model);
    }

    /** See {@link GraphInteraction#beginExternalDrag}. */
    public void beginExternalDrag(StageGraphModel.Node unplaced, double mx, double my) {
        interaction.beginExternalDrag(unplaced, mx, my);
    }

    /** True while a press on a node or an unplaced stage may still turn into a drag. */
    public boolean isDragging() {
        return interaction.isDragArmed();
    }

    /** The unplaced stage being dragged onto the map right now, or null. */
    public StageGraphModel.Node draggedUnplaced() {
        return interaction.draggedUnplaced();
    }

    /** True while nodes already on the map are being dragged and could be dropped on the sidebar. */
    public boolean isDraggingPlacedNode() {
        return interaction.isDraggingPlacedNode();
    }

    public int draggedCount() {
        return interaction.draggedCount();
    }

    public String draggedLabel() {
        return interaction.draggedLabel();
    }

    public boolean mouseClicked(double mx, double my, int button) {
        return interaction.mouseClicked(mx, my, button);
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return interaction.mouseDragged(mx, my, button, dx, dy);
    }

    public boolean mouseReleased(double mx, double my, int button) {
        return interaction.mouseReleased(mx, my, button);
    }

    public boolean mouseScrolled(double mx, double my, double scrollY) {
        return camera.scroll(mx, my, scrollY, model);
    }
}
