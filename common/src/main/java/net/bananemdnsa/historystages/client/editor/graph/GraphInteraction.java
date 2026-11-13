package net.bananemdnsa.historystages.client.editor.graph;

import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.graph.GraphPos;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Mouse handling on the graph: panning, the selection, dragging nodes (one or a whole selection),
 * and dragging stages in from the unplaced list. A drop is resolved into complete position maps
 * and handed to the screen through {@link GraphCanvas.DragHandler}; nothing in the model moves
 * until the screen commits it.
 */
final class GraphInteraction {

    /** Screen pixels a press must travel before it counts as a drag rather than a click. */
    private static final int DRAG_THRESHOLD = 4;
    private static final int DROP_TARGET_COLOR = 0x80FFCC00;
    private static final int GHOST_BG = 0xE0101010;
    private static final int GHOST_ACCENT = 0xFFFFCC00;
    private static final int BAND_FILL = 0x1AFFCC00;
    private static final int BAND_EDGE = 0xFFFFCC00;

    private final GraphCamera camera;
    private final GraphRenderer renderer;
    /** Whether authoring (dragging, selecting, placing) is available at all — editor mode only. */
    private final boolean editor;
    /** Called after the model was changed in place, so the draw order can follow. */
    private final Runnable onModelMutated;
    private StageGraphModel model;

    private boolean panning;

    // Node/unplaced drag: armed on press, promoted to a real drag past DRAG_THRESHOLD, resolved
    // (applied or discarded) on release. Mirrors the arm-then-threshold pattern in
    // StageOverviewScreen (armDrag/mouseDragged/mouseReleased) rather than inventing a new one.
    private boolean dragArmed;
    private boolean dragStarted;
    private double pressX, pressY;
    /** Graph key of the node being dragged, or null while dragging an unplaced stage in from the sidebar. */
    private String draggedKey;
    /** Index into {@code model.unplaced()} being dragged, or -1 when dragging an existing node. */
    private int draggedUnplacedIndex = -1;

    /** Graph keys picked with Ctrl. Dragging one of them moves all of them. */
    private final Set<String> selection = new LinkedHashSet<>();
    /** Rubber band while Ctrl-dragging on empty space, as {x1, y1, x2, y2} in screen space; null otherwise. */
    private double[] band;

    private GraphCanvas.DragHandler dragHandler;
    private GraphCanvas.RemoveTarget removeTarget;

    GraphInteraction(GraphCamera camera, GraphRenderer renderer, boolean editor, Runnable onModelMutated) {
        this.camera = camera;
        this.renderer = renderer;
        this.editor = editor;
        this.onModelMutated = onModelMutated;
    }

    void setDragHandler(GraphCanvas.DragHandler handler) {
        this.dragHandler = handler;
    }

    void setRemoveTarget(GraphCanvas.RemoveTarget target) {
        this.removeTarget = target;
    }

    void setModel(StageGraphModel model) {
        this.model = model;
        // A fresh model invalidates any in-flight drag reference (index/key from the old one),
        // and a selected stage that is no longer on the map cannot stay selected.
        clearDrag();
        band = null;
        if (model == null) selection.clear();
        else selection.retainAll(model.nodes().keySet());
    }

    // --- Selection ----------------------------------------------------------------------------

    Set<String> selection() {
        return Collections.unmodifiableSet(selection);
    }

    void select(Collection<String> graphKeys) {
        selection.addAll(graphKeys);
    }

    void clearSelection() {
        selection.clear();
    }

    /** Every stage on the map; the unplaced ones in the sidebar are not part of it. */
    void selectAll() {
        if (model != null) selection.addAll(model.nodes().keySet());
    }

    /** True while the mouse is in the middle of something — a pan, a drag or a rubber band. */
    boolean isBusy() {
        return dragArmed || panning || band != null;
    }

    // --- Drag state the screen and sidebar look at ---------------------------------------------

    /**
     * A press on an unplaced stage in the sidebar. From here on it is an ordinary drag: the
     * sidebar starts it, the canvas follows the mouse and resolves the drop.
     */
    void beginExternalDrag(StageGraphModel.Node unplaced, double mx, double my) {
        if (!editor || model == null) return;
        int index = model.unplaced().indexOf(unplaced);
        if (index >= 0) armDrag(null, index, mx, my);
    }

    /** True from the press that armed a node or unplaced drag until its release. */
    boolean isDragArmed() {
        return dragArmed;
    }

    /** The unplaced stage being dragged onto the map right now, or null. */
    StageGraphModel.Node draggedUnplaced() {
        if (!dragStarted || model == null || draggedUnplacedIndex < 0
                || draggedUnplacedIndex >= model.unplaced().size()) return null;
        return model.unplaced().get(draggedUnplacedIndex);
    }

    /** True while nodes already on the map are being dragged, i.e. could be dropped on the sidebar. */
    boolean isDraggingPlacedNode() {
        return dragStarted && draggedKey != null;
    }

    /** How many nodes the current drag carries. */
    int draggedCount() {
        return draggedKey == null ? (draggedUnplacedIndex >= 0 ? 1 : 0) : draggedKeys(draggedKey).size();
    }

    /** Name of the stage being dragged, for a single drag. */
    String draggedLabel() {
        if (model == null) return null;
        if (draggedKey != null) {
            StageGraphModel.Node node = model.nodes().get(draggedKey);
            return node == null ? null : labelFor(node);
        }
        StageGraphModel.Node unplaced = draggedUnplaced();
        return unplaced == null ? null : labelFor(unplaced);
    }

    /** The whole selection when the grabbed node is part of it, otherwise just that node. */
    private Set<String> draggedKeys(String key) {
        return selection.contains(key) ? new LinkedHashSet<>(selection) : Set.of(key);
    }

    private void clearDrag() {
        dragArmed = false;
        dragStarted = false;
        draggedKey = null;
        draggedUnplacedIndex = -1;
    }

    // --- Drawing ------------------------------------------------------------------------------

    /** Inside the map's clip and camera transform: the cells a drag would land on. */
    void renderInMap(GuiGraphics g, int mouseX, int mouseY) {
        if (!dragStarted || model == null) return;
        StageGraphModel.Node grabbed = draggedKey == null ? null : model.nodes().get(draggedKey);
        if (grabbed == null) {
            drawDropTarget(g, camera.gridX(mouseX), camera.gridY(mouseY));
            return;
        }
        // A group shows where every member lands, so the author sees the shape arrive intact.
        int dx = camera.gridX(mouseX) - grabbed.pos().x();
        int dy = camera.gridY(mouseY) - grabbed.pos().y();
        for (String key : draggedKeys(draggedKey)) {
            StageGraphModel.Node node = model.nodes().get(key);
            if (node != null) drawDropTarget(g, node.pos().x() + dx, node.pos().y() + dy);
        }
    }

    /** Fixed on screen, above the map: the rubber band and the label following the cursor. */
    void renderOverlay(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (band != null) drawBand(g);
        if (dragStarted) drawDragGhost(g, font, mouseX, mouseY);
    }

    /** Crosshair over a grid cell. */
    private void drawDropTarget(GuiGraphics g, int gridX, int gridY) {
        int cx = camera.screenX(gridX);
        int cy = camera.screenY(gridY);
        int r = Math.max(6, Math.round(GraphRenderer.BASE_NODE_RADIUS * camera.zoom()));
        g.fill(cx - r, cy - 1, cx + r, cy + 1, DROP_TARGET_COLOR);
        g.fill(cx - 1, cy - r, cx + 1, cy + r, DROP_TARGET_COLOR);
    }

    private void drawBand(GuiGraphics g) {
        int x1 = (int) Math.min(band[0], band[2]);
        int y1 = (int) Math.min(band[1], band[3]);
        int x2 = (int) Math.max(band[0], band[2]);
        int y2 = (int) Math.max(band[1], band[3]);
        g.enableScissor(camera.viewX(), camera.viewY(), camera.viewX() + camera.viewW(), camera.viewY() + camera.viewH());
        g.fill(x1, y1, x2, y2, BAND_FILL);
        g.fill(x1, y1, x2, y1 + 1, BAND_EDGE);
        g.fill(x1, y2 - 1, x2, y2, BAND_EDGE);
        g.fill(x1, y1, x1 + 1, y2, BAND_EDGE);
        g.fill(x2 - 1, y1, x2, y2, BAND_EDGE);
        g.disableScissor();
    }

    /** Small label following the cursor, naming whatever is currently being dragged. */
    private void drawDragGhost(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int count = draggedCount();
        String label = count > 1
                ? Component.translatable("editor.historystages.graph.drag.many", count).getString()
                : draggedLabel();
        if (label == null || label.isEmpty()) return;

        int w = font.width(label) + 8;
        int gx = mouseX + 10;
        int gy = mouseY + 10;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300); // above nodes/edges, below modal dialogs
        g.fill(gx, gy, gx + w, gy + 14, GHOST_BG);
        g.fill(gx, gy + 13, gx + w, gy + 14, GHOST_ACCENT);
        g.drawString(font, label, gx + 4, gy + 3, 0xFFFFFFFF, false);
        g.pose().popPose();
    }

    private static String labelFor(StageGraphModel.Node node) {
        return node.label() == null || node.label().isEmpty() ? node.stageId() : node.label();
    }

    // --- Input --------------------------------------------------------------------------------

    boolean mouseClicked(double mx, double my, int button) {
        if (!camera.within(mx, my)) return false;

        // Middle mouse always pans, even with a node under the cursor. Left-drag on empty space
        // pans too, but that is unusable in the editor once the map is dense — there is barely
        // any empty space to grab, and every miss opens the detail panel over what you wanted
        // to look at.
        if (button == 2) {
            panning = true;
            return true;
        }
        if (button != 0) return false;

        String hit = renderer.nodeAt(mx, my);

        // Ctrl never moves anything: on a node it toggles that node, on empty space it starts a
        // rubber band. Plain left-drag keeps panning, so nothing an author already does changes.
        if (editor && Screen.hasControlDown()) {
            if (hit != null) {
                if (!selection.remove(hit)) selection.add(hit);
            } else {
                band = new double[]{mx, my, mx, my};
            }
            return true;
        }

        // A press on a node arms a drag instead of panning; a press on empty space arms panning
        // as before. The two can never both fire from the same press, so dragging a node never
        // also moves the camera underneath it.
        if (editor && hit != null) {
            // Grabbing a node outside the selection is a fresh, single gesture.
            if (!selection.contains(hit)) selection.clear();
            armDrag(hit, -1, mx, my);
        } else if (hit == null) {
            panning = true;
        }
        return true;
    }

    /** Records a press without acting on it yet — see {@link #mouseDragged}/{@link #mouseReleased}. */
    private void armDrag(String key, int unplacedIndex, double mx, double my) {
        dragArmed = true;
        dragStarted = false;
        pressX = mx;
        pressY = my;
        draggedKey = key;
        draggedUnplacedIndex = unplacedIndex;
    }

    boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (panning) {
            camera.pan(dx, dy, model);
            return true;
        }
        if (band != null && button == 0) {
            band[2] = mx;
            band[3] = my;
            return true;
        }
        if (dragArmed && button == 0) {
            if (!dragStarted) {
                double ddx = mx - pressX;
                double ddy = my - pressY;
                if (ddx * ddx + ddy * ddy > (double) DRAG_THRESHOLD * DRAG_THRESHOLD) {
                    dragStarted = true;
                }
            }
            return true;
        }
        return false;
    }

    boolean mouseReleased(double mx, double my, int button) {
        if ((button == 0 || button == 2) && panning) {
            panning = false;
            camera.snapCameraToPixel();
            return true;
        }
        if (button == 0 && band != null) {
            selectInBand();
            band = null;
            return true;
        }
        if (button == 0 && dragArmed) {
            boolean started = dragStarted;
            String key = draggedKey;
            int unplacedIdx = draggedUnplacedIndex;
            Set<String> carried = key == null ? Set.of() : draggedKeys(key);
            clearDrag();
            if (!started) return true;
            if (key != null && removeTarget != null && removeTarget.contains(mx, my)) {
                removeTarget.onRemove(carried);
                return true;
            }
            finishDrag(key, carried, unplacedIdx, mx, my);
            return true;
        }
        return false;
    }

    /** Adds every node whose centre lies inside the rubber band. */
    private void selectInBand() {
        if (model == null) return;
        double x1 = Math.min(band[0], band[2]), x2 = Math.max(band[0], band[2]);
        double y1 = Math.min(band[1], band[3]), y2 = Math.max(band[1], band[3]);
        for (Map.Entry<String, StageGraphModel.Node> e : model.nodes().entrySet()) {
            int cx = camera.screenX(e.getValue().pos().x());
            int cy = camera.screenY(e.getValue().pos().y());
            if (cx >= x1 && cx <= x2 && cy >= y1 && cy <= y2) selection.add(e.getKey());
        }
    }

    /**
     * Resolves a completed drag into the complete position map of every tree it touches and
     * hands that to the host via {@link #dragHandler} — the host decides whether to show the
     * freeze confirmation and, if so, only calls the supplied {@code commit} once the author agrees.
     */
    private void finishDrag(String key, Set<String> carried, int unplacedIdx, double mx, double my) {
        if (model == null || dragHandler == null || !camera.within(mx, my)) return;

        Map<StageGraphModel.Node, GraphPos> moves = new LinkedHashMap<>();
        if (unplacedIdx >= 0) {
            if (unplacedIdx >= model.unplaced().size()) return;
            moves.put(model.unplaced().get(unplacedIdx), GraphPos.of(camera.gridX(mx), camera.gridY(my)));
        } else if (key != null) {
            StageGraphModel.Node grabbed = model.nodes().get(key);
            if (grabbed == null) return;
            int dx = camera.gridX(mx) - grabbed.pos().x();
            int dy = camera.gridY(my) - grabbed.pos().y();
            if (dx == 0 && dy == 0) return; // released back where it started; nothing to report
            for (String k : carried) {
                StageGraphModel.Node node = model.nodes().get(k);
                if (node != null) moves.put(node, GraphPos.of(node.pos().x() + dx, node.pos().y() + dy));
            }
        }
        if (moves.isEmpty()) return;
        dragHandler.onDrop(positionsAfter(moves), () -> commit(moves));
    }

    /**
     * For every tree a move touches: that tree's complete position map as it would be once the
     * moves are applied. Keyed by the individual flag. A tree the moves leave alone is not in it,
     * so dragging global stages never freezes the individual tree as a side effect.
     */
    private Map<Boolean, Map<String, GraphPos>> positionsAfter(Map<StageGraphModel.Node, GraphPos> moves) {
        Set<Boolean> touched = new TreeSet<>();
        for (StageGraphModel.Node node : moves.keySet()) touched.add(node.individual());

        Map<Boolean, Map<String, GraphPos>> out = new LinkedHashMap<>();
        for (boolean individual : touched) {
            Map<String, GraphPos> tree = new LinkedHashMap<>();
            for (StageGraphModel.Node node : model.nodes().values()) {
                if (node.individual() == individual) tree.put(node.stageId(), node.pos());
            }
            for (Map.Entry<StageGraphModel.Node, GraphPos> move : moves.entrySet()) {
                if (move.getKey().individual() == individual) tree.put(move.getKey().stageId(), move.getValue());
            }
            out.put(individual, tree);
        }
        return out;
    }

    /** Applies the moves to the model in place: placed nodes move, unplaced ones join the map. */
    private void commit(Map<StageGraphModel.Node, GraphPos> moves) {
        for (Map.Entry<StageGraphModel.Node, GraphPos> move : moves.entrySet()) {
            StageGraphModel.Node original = move.getKey();
            model.unplaced().remove(original);
            StageGraphModel.Node moved = new StageGraphModel.Node(original.stageId(), original.individual(),
                    move.getValue(), original.state(), original.anonymous(), original.label(), original.icon());
            model.nodes().put(StageManager.graphKey(original.stageId(), original.individual()), moved);
        }
        onModelMutated.run();
    }
}
