package net.bananemdnsa.historystages.client.editor;

import net.bananemdnsa.historystages.GraphConfig;
import net.bananemdnsa.historystages.client.cache.ClientIndividualStageCache;
import net.bananemdnsa.historystages.client.cache.ClientStageCache;
import net.bananemdnsa.historystages.client.editor.dialog.StageInfoTextScreen;
import net.bananemdnsa.historystages.client.editor.dialog.StylePresetPickerScreen;
import net.bananemdnsa.historystages.client.editor.graph.CanvasBackgrounds;
import net.bananemdnsa.historystages.client.editor.graph.GraphCanvas;
import net.bananemdnsa.historystages.client.editor.graph.GraphDetailScreen;
import net.bananemdnsa.historystages.client.editor.graph.GraphKeys;
import net.bananemdnsa.historystages.client.editor.graph.GraphLayoutHistory;
import net.bananemdnsa.historystages.client.editor.graph.GraphLayoutHistory.Step;
import net.bananemdnsa.historystages.client.editor.graph.GraphLayoutHistory.TreeState;
import net.bananemdnsa.historystages.client.editor.graph.GraphLegend;
import net.bananemdnsa.historystages.client.editor.graph.GraphShortcutPanel;
import net.bananemdnsa.historystages.client.editor.graph.GraphSidebar;
import net.bananemdnsa.historystages.client.editor.graph.GraphViewFilter;
import net.bananemdnsa.historystages.client.editor.graph.StageGraphConfig;
import net.bananemdnsa.historystages.client.editor.graph.StageGraphModel;
import net.bananemdnsa.historystages.client.editor.graph.StageStyleClipboard;
import net.bananemdnsa.historystages.client.editor.toast.EditorToast;
import net.bananemdnsa.historystages.client.editor.toast.EditorToastHandler;
import net.bananemdnsa.historystages.client.editor.widget.ConfirmDialog;
import net.bananemdnsa.historystages.client.editor.widget.ContextMenu;
import net.bananemdnsa.historystages.client.editor.widget.EditorTooltip;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.graph.GraphBranch;
import net.bananemdnsa.historystages.data.graph.GraphLayoutData;
import net.bananemdnsa.historystages.data.graph.GraphPos;
import net.bananemdnsa.historystages.data.graph.GraphStageData;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.serverbound.RearrangeGraphPacket;
import net.bananemdnsa.historystages.network.serverbound.SaveGraphPositionsPacket;
import net.bananemdnsa.historystages.network.serverbound.AssignStylePresetPacket;
import net.bananemdnsa.historystages.network.serverbound.SaveStageGraphStylePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Host screen for the stage graph: composes {@link GraphSidebar} and {@link GraphCanvas} into
 * one view, opened either from the in-game editor or from the pause screen. Clicking a node
 * opens {@link GraphDetailScreen} over it.
 *
 * <p>One class with a {@link Mode} flag rather than two screen classes — two views onto the
 * same map whose rendering drifts apart is the worst outcome available here. The two modes
 * differ only in which {@link GraphViewFilter} builds the model, whether the editor's
 * navigation button is shown, and whether the view fits itself to the visible nodes on open;
 * everything else (layout, rendering, input routing) is shared.
 */
public class StageGraphScreen extends Screen {

    public enum Mode { PLAYER, EDITOR }

    private static final int TOP_BAR_H = 24;

    /**
     * Side column widths. Both are capped against the window so the canvas keeps the majority of
     * the screen: the fixed 200/260 they started at ate well over half a 1280-wide window once
     * both were open, which is the wrong split for a view whose whole point is the map.
     */
    private static final int SIDEBAR_W = 165;
    /** The sidebar may not take more than this share of the window. */
    private static final float MAX_COLUMN_SHARE = 0.22f;

    /** Movement below this, in pixels, still counts as a click rather than a drag. */
    private static final int CLICK_SLOP = 4;

    private double pressX;
    private double pressY;
    private boolean pressedOnCanvas;

    /** Combined unlock-cache version last folded into {@link #model}; see refreshOnUnlockChange. */
    private int lastUnlockVersion = Integer.MIN_VALUE;
    /** The graph_stages.json snapshot the current background was resolved from. */
    private GraphStageData.Snapshot backgroundSource;

    private static final int BACK_BUTTON_X = 6;
    private static final int BACK_BUTTON_Y = 2;
    private static final int BACK_BUTTON_W = 60;
    private static final int BACK_BUTTON_H = 18;

    private static final int REARRANGE_BUTTON_X = BACK_BUTTON_X + BACK_BUTTON_W + 6;
    private static final int REARRANGE_BUTTON_W = 90;

    private static final int PRESETS_BUTTON_X = REARRANGE_BUTTON_X + REARRANGE_BUTTON_W + 6;
    private static final int PRESETS_BUTTON_W = 70;

    /** Undo, redo and "?" sit right in the top bar, away from Re-arrange on the left. */
    private static final int ICON_BUTTON_W = 18;
    private static final int ICON_BUTTON_GAP = 4;
    private static final int TOP_BAR_RIGHT_MARGIN = 6;

    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int BACKGROUND_COLOR = 0xFF101010;

    private final Screen parent;
    private final Mode mode;

    private GraphCanvas canvas;
    /** Null in the player view whenever {@code [general] showSidebar} is off — this screen owns that decision. */
    private GraphSidebar sidebar;
    /** Null whenever {@code [general] showLegend} is off — this screen owns that decision too. */
    private GraphLegend legend;
    private StageGraphModel model;

    /** Fresh instance per open, exactly as {@code StageOverviewScreen} does it. Editor mode only. */
    private ContextMenu contextMenu;

    /** The stage whose detail window is open over this screen, or null. */
    private String detailKey;

    private final GraphLayoutHistory history = GraphLayoutHistory.CLIENT;
    private final EditorTooltip tooltip = new EditorTooltip();
    private final GraphShortcutPanel shortcutPanel = new GraphShortcutPanel();
    private boolean shortcutsOpen;
    /** Editor mode only; null in the player view. */
    private StyledButton undoButton;
    private StyledButton redoButton;
    private StyledButton helpButton;

    /** How the history reads and restores a tree: the same packets a drag or Re-arrange sends. */
    private final GraphLayoutHistory.Applier applier = new GraphLayoutHistory.Applier() {
        @Override
        public TreeState current(boolean individual) {
            return capture(Set.of(individual)).get(individual);
        }

        @Override
        public void apply(boolean individual, TreeState state) {
            if (state.frozen()) {
                sendPositions(individual, state.positions());
                return;
            }
            // Back to before the first drag: the algorithm owns this tree again.
            PacketHandler.sendToServer(new RearrangeGraphPacket(individual));
            GraphLayoutData.Snapshot cur = GraphLayoutData.get();
            GraphLayoutData.set(individual
                    ? new GraphLayoutData.Snapshot(cur.global(), Map.of(), cur.globalFrozen(), false)
                    : new GraphLayoutData.Snapshot(Map.of(), cur.individual(), false, cur.individualFrozen()));
            StageManager.recomputeGraphLayout();
        }
    };

    /** Editor view — from {@code StageOverviewScreen}. Unfiltered, with the authoring tools. */
    public StageGraphScreen(Screen parent) {
        this(parent, Mode.EDITOR);
    }

    /** Player view — from the pause screen. Filtered, read-only. */
    public static StageGraphScreen forPlayer(Screen parent) {
        return new StageGraphScreen(parent, Mode.PLAYER);
    }

    private StageGraphScreen(Screen parent, Mode mode) {
        super(resolveTitle());
        this.parent = parent;
        this.mode = mode;
    }

    /**
     * Literal text, unless a translation exists for it — mirrors {@code GraphDetailScreen.describe},
     * down to the {@code &} colour codes literal text may carry. That is what the rich text editor
     * behind this config key writes.
     */
    private static Component resolveTitle() {
        String raw = GraphConfig.GRAPH.title.get();
        return I18n.exists(raw) ? Component.translatable(raw)
                : Component.literal(raw.replace('&', '§'));
    }

    @Override
    protected void init() {
        boolean editorView = mode == Mode.EDITOR;
        model = buildModel();

        int contentTop = TOP_BAR_H;
        int contentHeight = Math.max(0, this.height - TOP_BAR_H);

        int columnCap = Math.max(90, Math.round(this.width * MAX_COLUMN_SHARE));
        // The editor always gets the sidebar: it holds the unplaced stages and is where stages are
        // dropped to take them off the map. showSidebar is a player-view setting.
        boolean showSidebar = editorView || GraphConfig.GRAPH.showSidebar.get();
        int sidebarFullW = showSidebar ? Math.min(SIDEBAR_W, columnCap) : 0;

        // A ConfirmDialog is a full screen swap, so confirming Re-arrange or a freeze re-enters
        // this method on the very same instance. Reusing the existing canvas/sidebar (updating
        // their bounds and model in place) rather than constructing fresh ones keeps the
        // author's pan/zoom and sidebar scroll exactly where they left them; only the very first
        // open constructs anything.
        if (canvas == null) {
            canvas = new GraphCanvas(model, sidebarFullW, contentTop,
                    Math.max(0, this.width - sidebarFullW), contentHeight, editorView);
            canvas.setDragHandler(this::handleDrop);
            canvas.setRemoveTarget(new GraphCanvas.RemoveTarget() {
                @Override
                public boolean contains(double mx, double my) {
                    return sidebar != null && sidebar.containsPoint(mx, my);
                }

                @Override
                public void onRemove(Set<String> graphKeys) {
                    removeFromGraph(graphKeys);
                }
            });
        } else {
            canvas.setModel(model);
        }
        // Back from the detail window: its ring only said which stage the window was about.
        if (detailKey != null) {
            if (detailKey.equals(canvas.focusedKey())) canvas.highlight(null);
            detailKey = null;
        }
        refreshBackground();

        if (showSidebar) {
            if (sidebar == null) {
                sidebar = new GraphSidebar(canvas, editorView);
                sidebar.setTrayPressHandler(canvas::beginExternalDrag);
                // A list click centres the map and marks the stage — it deliberately does not
                // open the detail window. Paging down the list would otherwise throw a modal up
                // and down on every single click. The window is reached through a node.
                sidebar.setSelectHandler(canvas::highlight);
            }
            sidebar.setBounds(0, contentTop, sidebarFullW, contentHeight);
            sidebar.setModel(model);
        } else {
            sidebar = null;
        }

        // The sidebar owns how wide it currently draws itself — it may be collapsed to its rail,
        // or mid-animation after a re-init — so the canvas is positioned from that, not from the
        // configured width.
        int sidebarW = sidebar == null ? 0 : sidebar.currentWidth();
        canvas.setBounds(sidebarW, contentTop, Math.max(0, this.width - sidebarW), contentHeight);

        // Explains the map to whoever is looking at it, so it is gated only on showLegend — not
        // on editor vs. player mode, unlike the back/re-arrange buttons below. Bounded to the
        // canvas's own area, which is now everything right of the sidebar: the detail view is a
        // window rather than a docked strip, so nothing on the right needs keeping clear.
        if (GraphConfig.GRAPH.showLegend.get()) {
            if (legend == null) legend = new GraphLegend();
            legend.setBounds(sidebarW, contentTop, Math.max(0, this.width - sidebarW), contentHeight);
        } else {
            legend = null;
        }

        if (editorView) {
            // Player view relies on ESC (its parent is the pause screen) and must not offer any
            // editor-style navigation or authoring control.
            this.addRenderableWidget(StyledButton.of(
                    Component.translatable("editor.historystages.back"),
                    btn -> this.minecraft.setScreen(parent),
                    BACK_BUTTON_X, BACK_BUTTON_Y, BACK_BUTTON_W, BACK_BUTTON_H));
            this.addRenderableWidget(StyledButton.of(
                    Component.translatable("editor.historystages.graph.rearrange"),
                    btn -> confirmRearrange(),
                    REARRANGE_BUTTON_X, BACK_BUTTON_Y, REARRANGE_BUTTON_W, BACK_BUTTON_H));
        } else if (GraphConfig.GRAPH.fitOnOpen.get()) {
            // Without this a filtered map opens on empty space: positions are frozen, so
            // filtered-out stages leave real gaps rather than shrinking the layout.
            canvas.fitToContent();
        }

        int iconX = this.width - TOP_BAR_RIGHT_MARGIN - ICON_BUTTON_W;
        helpButton = this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.graph.button.help"),
                btn -> shortcutsOpen = !shortcutsOpen,
                iconX, BACK_BUTTON_Y, ICON_BUTTON_W, BACK_BUTTON_H));
        if (editorView) {
            iconX -= ICON_BUTTON_W + ICON_BUTTON_GAP;
            redoButton = this.addRenderableWidget(StyledButton.of(
                    Component.translatable("editor.historystages.graph.button.redo"),
                    btn -> travel(false),
                    iconX, BACK_BUTTON_Y, ICON_BUTTON_W, BACK_BUTTON_H));
            iconX -= ICON_BUTTON_W + ICON_BUTTON_GAP;
            undoButton = this.addRenderableWidget(StyledButton.of(
                    Component.translatable("editor.historystages.graph.button.undo"),
                    btn -> travel(true),
                    iconX, BACK_BUTTON_Y, ICON_BUTTON_W, BACK_BUTTON_H));

            // Next to Re-arrange when the title leaves room for it, otherwise in front of the
            // undo group — at a large GUI scale the left group runs into the centred title.
            int titleLeft = this.width / 2 - this.font.width(this.title) / 2;
            int presetsX = PRESETS_BUTTON_X + PRESETS_BUTTON_W + 4 <= titleLeft
                    ? PRESETS_BUTTON_X
                    : iconX - ICON_BUTTON_GAP - PRESETS_BUTTON_W;
            this.addRenderableWidget(StyledButton.of(
                    Component.translatable("editor.historystages.graph.preset.manage"),
                    btn -> this.minecraft.setScreen(new StylePresetsScreen(this)),
                    presetsX, BACK_BUTTON_Y, PRESETS_BUTTON_W, BACK_BUTTON_H));
        }
    }

    private StageGraphModel buildModel() {
        boolean editorView = mode == Mode.EDITOR;
        GraphViewFilter filter = editorView ? GraphViewFilter.passThrough() : GraphViewFilter.fromConfig();
        return StageGraphModel.build(filter, editorView);
    }

    /**
     * Rebuilds the model when the player's unlocked stages change while the graph is open.
     *
     * <p>Both a node's colour and an edge's met/open state are baked into the model at build
     * time, and the model was only ever built in {@link #init}. Unlocking a stage with the graph
     * open therefore left the picture half-updated until it was reopened — the very moment the
     * view is most worth watching.
     */
    private void refreshOnUnlockChange() {
        int version = ClientStageCache.version() + ClientIndividualStageCache.version();
        if (version == lastUnlockVersion) return;
        lastUnlockVersion = version;

        model = buildModel();
        canvas.setModel(model);
        if (sidebar != null) sidebar.setModel(model);
        // Styles are resolved per lock state, so every cached one is now suspect.
        StageGraphConfig.invalidateCache();
        refreshBackground();
    }

    /**
     * The player's map takes the background of their latest unlock that sets one. The editor
     * always shows graph.toml's, so what an author sees while building does not depend on which
     * stages their own character happens to hold.
     */
    private void refreshBackground() {
        backgroundSource = GraphStageData.get();
        canvas.setBackground(mode == Mode.PLAYER ? CanvasBackgrounds.forPlayer() : null);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No-op — render() draws its own opaque background; this avoids 1.21's menu blur shader.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, BACKGROUND_COLOR);

        refreshOnUnlockChange();
        // An author saving a background while this map is open replaces the snapshot.
        if (GraphStageData.get() != backgroundSource) refreshBackground();

        // The sidebar can be collapsed to a rail, and animates between the two widths. Its edge
        // is where the canvas viewport starts, so both it and the legend are re-bounded every
        // frame; setBounds only moves the viewport and leaves pan and zoom untouched, so the map
        // holds still and simply reveals more area on the left.
        if (sidebar != null) {
            int sidebarW = sidebar.advanceWidth();
            int contentHeight = Math.max(0, this.height - TOP_BAR_H);
            canvas.setBounds(sidebarW, TOP_BAR_H, Math.max(0, this.width - sidebarW), contentHeight);
            if (legend != null) {
                legend.setBounds(sidebarW, TOP_BAR_H, Math.max(0, this.width - sidebarW), contentHeight);
            }
        }

        canvas.render(g, this.font, mouseX, mouseY, partialTick);
        if (sidebar != null) {
            sidebar.setDraggingFromTray(canvas.draggedUnplaced());
            if (mode == Mode.EDITOR) {
                String hint = null;
                if (canvas.isDraggingPlacedNode()) {
                    int count = canvas.draggedCount();
                    hint = count == 1
                            ? Component.translatable("editor.historystages.graph.drop.remove_one", canvas.draggedLabel()).getString()
                            : Component.translatable("editor.historystages.graph.drop.remove_many", count).getString();
                }
                sidebar.setDropHint(hint, hint != null && sidebar.containsPoint(mouseX, mouseY));
            }
            sidebar.render(g, this.font, mouseX, mouseY);
        }
        if (legend != null) {
            legend.render(g, this.font, mouseX, mouseY);
        }

        g.drawCenteredString(this.font, this.title, this.width / 2, 8, TITLE_COLOR);

        if (undoButton != null) undoButton.active = history.canUndo();
        if (redoButton != null) redoButton.active = history.canRedo();
        Component help = Component.translatable("editor.historystages.graph.button.help");
        helpButton.setMessage(shortcutsOpen ? help.copy().withStyle(ChatFormatting.GOLD) : help);

        super.render(g, mouseX, mouseY, partialTick); // the top-bar buttons

        if (shortcutsOpen) {
            shortcutPanel.layout(this.font, this.width - TOP_BAR_RIGHT_MARGIN, TOP_BAR_H + 2, mode == Mode.EDITOR);
            // Item icons on the map are drawn around z 150, so anything lower lets them shine
            // through. Same layer as the legend, the other panel floating over the map.
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            shortcutPanel.render(g, this.font);
            g.pose().popPose();
        }

        // Rendered last, above everything above, with a z-translate — same convention as
        // StageOverviewScreen's context menu. Above the shortcut panel, which can sit under it.
        if (contextMenu != null && contextMenu.isVisible()) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 450);
            contextMenu.render(g, this.font, mouseX, mouseY);
            g.pose().popPose();
        } else {
            renderButtonTooltip(g, mouseX, mouseY);
        }
    }

    /**
     * The editor's own tooltip, not Minecraft's. Hover is worked out from the bounds directly,
     * because a greyed-out button does not report hover — and "nothing to undo" is exactly when
     * the tooltip is worth reading.
     */
    private void renderButtonTooltip(GuiGraphics g, int mouseX, int mouseY) {
        String key = null;
        String text = null;
        if (over(undoButton, mouseX, mouseY)) {
            key = "undo";
            text = describe(history.peekUndo(), "undo") + "\n§8" + keyLabel("undo");
        } else if (over(redoButton, mouseX, mouseY)) {
            key = "redo";
            text = describe(history.peekRedo(), "redo") + "\n§8" + keyLabel("redo");
        } else if (over(helpButton, mouseX, mouseY)) {
            key = "help";
            text = Component.translatable("editor.historystages.graph.keys.title").getString()
                    + "\n§8" + keyLabel("help");
        }
        tooltip.render(g, this.font, key, text, mouseX, mouseY, this.width, this.height);
    }

    private static boolean over(StyledButton button, int mx, int my) {
        return button != null && button.visible
                && mx >= button.getX() && mx < button.getX() + button.getWidth()
                && my >= button.getY() && my < button.getY() + button.getHeight();
    }

    private static String keyLabel(String shortcut) {
        return Component.translatable("editor.historystages.graph.keys." + shortcut + ".key").getString();
    }

    /** "Undo: moved 3 stages", or "Nothing to undo". */
    private static String describe(Step step, String direction) {
        String prefix = "editor.historystages.graph.history." + direction;
        if (step == null) return Component.translatable(prefix + ".none").getString();
        Component what = step.kind() == GraphLayoutHistory.Kind.REARRANGE
                ? Component.translatable("editor.historystages.graph.history.rearrange")
                : step.count() == 1
                ? Component.translatable("editor.historystages.graph.history."
                        + step.kind().name().toLowerCase(Locale.ROOT) + ".one")
                : Component.translatable("editor.historystages.graph.history."
                        + step.kind().name().toLowerCase(Locale.ROOT) + ".many", step.count());
        return Component.translatable(prefix, what).getString();
    }

    // --- Input --------------------------------------------------------------------------------
    //
    // Routing order is context menu, sidebar, legend, canvas: the context menu is rendered last
    // (on top of everything) and must claim a click anywhere before anything under it reacts —
    // same convention StageOverviewScreen uses. The sidebar sits in front of the canvas, so it
    // gets its turn first. The legend floats over the canvas area, so it is checked right before
    // the canvas — a header toggle or a click on its open body must never fall through to a node
    // underneath. The detail view is a separate screen and takes no part in this routing.

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (contextMenu != null && contextMenu.isVisible()) {
            contextMenu.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (shortcutsOpen && shortcutPanel.contains(mouseX, mouseY)) return true;
        if (sidebar != null && sidebar.mouseClicked(mouseX, mouseY, button)) return true;
        if (legend != null && legend.mouseClicked(mouseX, mouseY, button)) return true;
        if (mode == Mode.EDITOR && button == 1 && tryOpenContextMenu(mouseX, mouseY)) return true;
        if (handleCanvasClick(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Right-click on a node: builds and shows {@link #contextMenu}. Editor mode only. */
    private boolean tryOpenContextMenu(double mouseX, double mouseY) {
        String hit = canvas.nodeAt(mouseX, mouseY);
        if (hit == null) return false;
        StageGraphModel.Node node = model.nodes().get(hit);
        if (node == null) return false;

        contextMenu = new ContextMenu();
        contextMenu.addEntry(Component.translatable("editor.historystages.graph.context.edit_stage").getString(),
                () -> openStageEditor(node));
        contextMenu.addEntry(Component.translatable("editor.historystages.graph.context.edit_info").getString(),
                () -> this.minecraft.setScreen(new StageInfoTextScreen(this, node.stageId(), node.individual())));
        contextMenu.addEntry(Component.translatable("editor.historystages.graph.context.edit_style").getString(),
                () -> this.minecraft.setScreen(new StageStyleScreen(this, node.stageId(), node.individual())));
        contextMenu.addEntry(Component.translatable("editor.historystages.graph.context.copy_style").getString(),
                () -> copyStyle(node));
        if (!StageStyleClipboard.isEmpty()) {
            contextMenu.addEntry(Component.translatable("editor.historystages.graph.context.paste_style").getString(),
                    () -> pasteStyle(node));
        }
        contextMenu.addEntry(Component.translatable("editor.historystages.graph.context.select_branch").getString(),
                () -> canvas.select(GraphBranch.withDependents(hit, StageManager.graphPrerequisites(),
                        key -> model.nodes().containsKey(key))));
        // On a selected node the menu speaks for the whole selection, as a drag does.
        Set<String> selected = canvas.selection();
        Set<String> targets = selected.contains(hit) ? Set.copyOf(selected) : Set.of(hit);
        contextMenu.addEntry(targets.size() == 1
                        ? Component.translatable("editor.historystages.graph.context.assign_preset").getString()
                        : Component.translatable("editor.historystages.graph.context.assign_preset.many", targets.size()).getString(),
                () -> this.minecraft.setScreen(new StylePresetPickerScreen(this, targets.size(),
                        presetId -> assignPreset(targets, presetId),
                        () -> this.minecraft.setScreen(new StylePresetsScreen(this)))));
        contextMenu.addEntry(Component.translatable("editor.historystages.graph.context.remove").getString(),
                () -> removeFromGraph(targets));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        contextMenu.show((int) mouseX, (int) mouseY, this.font);
        return true;
    }

    private void openStageEditor(StageGraphModel.Node node) {
        Map<String, StageEntry> stages = node.individual()
                ? StageManager.getIndividualStages() : StageManager.getStages();
        StageEntry entry = stages.get(node.stageId());
        if (entry == null) return; // stage vanished (e.g. deleted from another client) between hit and click
        this.minecraft.setScreen(new StageDetailScreen(this, node.stageId(), entry, node.individual()));
    }

    /**
     * A node with nothing to copy leaves the clipboard alone. Overwriting it with an empty set
     * would destroy a style copied two nodes ago and make "Paste style" disappear from the menu,
     * with nothing on screen to explain either — so both outcomes say so with a toast.
     */
    private void copyStyle(StageGraphModel.Node node) {
        GraphStageData.Entry entry =
                GraphStageData.get().tree(node.individual()).get(node.stageId());
        if (entry == null || !entry.hasStyles()) {
            EditorToastHandler.show(EditorToast.Level.INFO,
                    Component.translatable("editor.historystages.graph.style.copy.empty.title"),
                    Component.translatable("editor.historystages.graph.style.copy.empty.message"));
            return;
        }
        StageStyleClipboard.copy(entry);
        EditorToastHandler.copiedToClipboard(node.stageId());
    }

    /**
     * Writes the clipboard onto a node, replacing whatever it had — a paste that left half the
     * old look in place would not be a paste.
     *
     * <p>Asks only when something would actually be lost. Always asking would make the case this
     * menu exists for, bringing several nodes into line quickly, tedious; never asking means a
     * misclick silently destroys hand-set values with no way back.
     */
    private void pasteStyle(StageGraphModel.Node node) {
        GraphStageData.Entry clipboard = StageStyleClipboard.get();
        if (clipboard == null) return;

        GraphStageData.Entry existing =
                GraphStageData.get().tree(node.individual()).get(node.stageId());
        boolean wouldOverwrite = existing != null && !existing.copyStyles().isEmpty();

        if (!wouldOverwrite) {
            applyPaste(node, clipboard);
            return;
        }

        this.minecraft.setScreen(new ConfirmDialog(this,
                Component.translatable("editor.historystages.graph.style.paste.title"),
                Component.translatable("editor.historystages.graph.style.paste.confirm"),
                () -> {
                    applyPaste(node, clipboard);
                    this.minecraft.setScreen(this);
                }));
    }

    private void applyPaste(StageGraphModel.Node node, GraphStageData.Entry clipboard) {
        PacketHandler.sendToServer(new SaveStageGraphStylePacket(
                node.stageId(), node.individual(), GraphStageData.entryToJson(clipboard)));
        // Same optimistic update the style screen does, and the same reason: on a dedicated
        // server the node would otherwise keep its old look until the broadcast returns.
        GraphStageData.set(GraphStageData.get()
                .withStyle(node.stageId(), node.individual(), clipboard));
        StageGraphConfig.invalidateCache();
    }

    /**
     * Points every target at a preset, or at none. One packet for the lot; the same change is
     * made locally right away, so the nodes behind the picker do not wait for the broadcast.
     */
    private void assignPreset(Set<String> graphKeys, String presetId) {
        List<AssignStylePresetPacket.Target> targets = new ArrayList<>();
        GraphStageData.Snapshot data = GraphStageData.get();
        for (String key : graphKeys) {
            StageGraphModel.Node node = model.nodes().get(key);
            if (node == null) continue;
            targets.add(new AssignStylePresetPacket.Target(node.stageId(), node.individual()));
            data = data.withAssignedPreset(node.stageId(), node.individual(), presetId);
        }
        if (targets.isEmpty()) return;
        PacketHandler.sendToServer(new AssignStylePresetPacket(targets, presetId == null ? "" : presetId));
        GraphStageData.set(data);
        StageGraphConfig.invalidateCache();
    }

    /**
     * Forwards the click to {@link GraphCanvas#mouseClicked}, which arms panning on empty space.
     * Returns false untouched when the click was outside the canvas viewport, so callers never
     * mistake an off-canvas click for one on the map.
     */
    private boolean handleCanvasClick(double mouseX, double mouseY, int button) {
        if (!canvas.mouseClicked(mouseX, mouseY, button)) return false;
        // Opening deliberately does NOT happen here — see mouseReleased. Opening the detail
        // window on press means grabbing a node to move it throws a modal up over the very area
        // you were about to drag into.
        // Ctrl-clicks build the selection and never open anything.
        if (button == 0 && !(mode == Mode.EDITOR && hasControlDown())) {
            pressX = mouseX;
            pressY = mouseY;
            pressedOnCanvas = true;
        }
        return true;
    }

    /**
     * Opens the detail window on a node.
     *
     * <p>Asking the server for the node's dependency data is the window's own job, not this
     * screen's. It used to be fired from here, guarded by a set of stages already asked for —
     * but a screen that is not the current one stops ticking, so nothing here could ever notice
     * a reply going missing and ask again.
     */
    private void openDetail(String graphKey) {
        StageGraphModel.Node node = model.nodes().get(graphKey);
        if (node == null) return; // vanished between hit test and release

        detailKey = graphKey;
        this.minecraft.setScreen(new GraphDetailScreen(this, model, node));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // Before the canvas: a scrollbar drag starts inside the sidebar but travels well past
        // its edges, and must not turn into a pan the moment the cursor leaves the strip.
        if (sidebar != null && sidebar.mouseDragged(mouseY)) return true;
        if (canvas.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (sidebar != null) sidebar.mouseReleased();
        boolean handled = canvas.mouseReleased(mouseX, mouseY, button);

        // A press that never turned into a drag is a click, and only then does the window open.
        // The same threshold the canvas uses to promote a press into a drag, so the two agree
        // on where a click stops being a click.
        if (pressedOnCanvas && button == 0) {
            pressedOnCanvas = false;
            double dx = mouseX - pressX;
            double dy = mouseY - pressY;
            if (dx * dx + dy * dy <= (double) CLICK_SLOP * CLICK_SLOP) {
                String hit = canvas.nodeAt(mouseX, mouseY);
                canvas.highlight(hit);
                if (hit != null) openDetail(hit);
                else canvas.clearSelection();
            }
            return true;
        }
        if (handled) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (shortcutsOpen && shortcutPanel.contains(mouseX, mouseY)) return true;
        if (sidebar != null && sidebar.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        if (canvas.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (sidebar != null && sidebar.charTyped(codePoint)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // The sidebar only consumes ESC while its filter dropdown is open; otherwise it falls
        // through to super, whose default shouldCloseOnEsc()/onClose() closes this screen.
        if (sidebar != null && sidebar.keyPressed(keyCode)) return true;
        boolean typing = sidebar != null && sidebar.isSearchFocused();
        // Esc peels off one layer at a time: the overview, then the selection, then the graph.
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && !typing) {
            if (shortcutsOpen) {
                shortcutsOpen = false;
                return true;
            }
            if (!canvas.selection().isEmpty()) {
                canvas.clearSelection();
                return true;
            }
        }
        boolean menuOpen = contextMenu != null && contextMenu.isVisible();
        if (!typing && !menuOpen && !canvas.isBusy()) {
            GraphKeys.Action action = GraphKeys.resolve(keyCode, GLFW.glfwGetKeyName(keyCode, scanCode),
                    hasControlDown(), hasShiftDown());
            if (action != null && runShortcut(action)) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** @return false when the key means nothing in this view, so it can fall through */
    private boolean runShortcut(GraphKeys.Action action) {
        switch (action) {
            case SEARCH -> {
                if (sidebar == null) return false;
                sidebar.focusSearch();
            }
            case FIT -> canvas.fitToContent();
            case ZOOM_IN -> canvas.zoomBy(1);
            case ZOOM_OUT -> canvas.zoomBy(-1);
            case ZOOM_RESET -> canvas.resetZoom();
            case HELP -> shortcutsOpen = !shortcutsOpen;
            default -> {
                return mode == Mode.EDITOR && runEditorShortcut(action);
            }
        }
        return true;
    }

    private boolean runEditorShortcut(GraphKeys.Action action) {
        switch (action) {
            case UNDO -> travel(true);
            case REDO -> travel(false);
            case SELECT_ALL -> canvas.selectAll();
            case SELECT_NONE -> canvas.clearSelection();
            case REMOVE -> {
                Set<String> targets = keyboardTargets();
                if (targets.isEmpty()) return false;
                removeFromGraph(targets);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * What Delete acts on: the selection, or else the stage picked in the sidebar list, which
     * keeps its ring on the map.
     */
    private Set<String> keyboardTargets() {
        if (!canvas.selection().isEmpty()) return Set.copyOf(canvas.selection());
        String focused = canvas.focusedKey();
        return focused != null && model.nodes().containsKey(focused) ? Set.of(focused) : Set.of();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    // --- Authoring: drag/freeze, re-arrange -----------------------------------------------

    /**
     * {@link GraphCanvas.DragHandler}: a drag just completed. The canvas has not moved anything
     * yet — {@code commit} does that. When every touched tree is already frozen it commits and
     * saves immediately; otherwise one confirmation covers all of them, and declining it leaves
     * the canvas untouched.
     */
    private void handleDrop(Map<Boolean, Map<String, GraphPos>> positions, Runnable commit) {
        savePositions(positions, commit);
    }

    /**
     * Takes stages off the map. Same path as a drop: each touched tree's complete position map,
     * minus these stages, goes out as one SaveGraphPositionsPacket — and an unfrozen tree asks
     * first, because taking something off is the moment the author starts owning the layout.
     */
    private void removeFromGraph(Set<String> graphKeys) {
        Map<Boolean, Map<String, GraphPos>> positions = new LinkedHashMap<>();
        for (boolean individual : new boolean[]{false, true}) {
            boolean touched = false;
            Map<String, GraphPos> tree = new LinkedHashMap<>();
            for (Map.Entry<String, StageGraphModel.Node> e : model.nodes().entrySet()) {
                StageGraphModel.Node node = e.getValue();
                if (node.individual() != individual) continue;
                if (graphKeys.contains(e.getKey())) touched = true;
                else tree.put(node.stageId(), node.pos());
            }
            if (touched) positions.put(individual, tree);
        }
        if (positions.isEmpty()) return;
        canvas.clearSelection();
        savePositions(positions, () -> {});
    }

    private void savePositions(Map<Boolean, Map<String, GraphPos>> positions, Runnable commit) {
        Runnable apply = () -> {
            Map<Boolean, TreeState> before = capture(positions.keySet());
            commit.run();
            positions.forEach(this::sendPositions);
            reloadModel();
            history.record(Step.between(before, capture(positions.keySet())));
        };
        boolean anyUnfrozen = false;
        for (boolean individual : positions.keySet()) {
            if (!GraphLayoutData.get().isFrozen(individual)) anyUnfrozen = true;
        }
        if (!anyUnfrozen) {
            apply.run();
            return;
        }
        this.minecraft.setScreen(new ConfirmDialog(this,
                Component.translatable("editor.historystages.graph.freeze.title"),
                Component.translatable("editor.historystages.graph.freeze.confirm"),
                () -> {
                    apply.run();
                    this.minecraft.setScreen(this);
                }));
    }

    /**
     * Rebuilds the model from the local layout snapshot, which {@link #sendPositions} has just
     * updated. Moving nodes in place is not enough: a stage placed from the tray has no edges
     * in the old model, and one taken off still has them.
     */
    private void reloadModel() {
        model = buildModel();
        canvas.setModel(model);
        if (sidebar != null) sidebar.setModel(model);
    }

    private void sendPositions(boolean individual, Map<String, GraphPos> positions) {
        PacketHandler.sendToServer(new SaveGraphPositionsPacket(individual, positions));
        // Optimistic local update, mirroring GraphLayoutData.freeze's in-memory effect only —
        // saving graph_layout.json is the server's job (the packet handler already does it);
        // doing it here too would write an unwanted copy into this client's own settings folder.
        GraphLayoutData.Snapshot cur = GraphLayoutData.get();
        Map<String, GraphPos> copy = new HashMap<>(positions);
        // Sending positions IS freezing, so the flag flips here too — otherwise the confirmation
        // would come back on the very next drag.
        GraphLayoutData.set(individual
                ? new GraphLayoutData.Snapshot(cur.global(), copy, cur.globalFrozen(), true)
                : new GraphLayoutData.Snapshot(copy, cur.individual(), true, cur.individualFrozen()));
    }

    private void confirmRearrange() {
        this.minecraft.setScreen(new ConfirmDialog(this,
                Component.translatable("editor.historystages.graph.rearrange"),
                Component.translatable("editor.historystages.graph.rearrange.confirm"),
                this::performRearrange));
    }

    private void performRearrange() {
        Set<Boolean> bothTrees = Set.of(false, true);
        Map<Boolean, TreeState> before = capture(bothTrees);
        PacketHandler.sendToServer(new RearrangeGraphPacket(false));
        PacketHandler.sendToServer(new RearrangeGraphPacket(true));

        // Optimistic local preview: GraphAutoLayout is pure, side-effect-free logic shared by
        // both sides, and StageManager.recomputeGraphLayout() deliberately never saves — calling
        // it here mirrors exactly what the server just kicked off, in memory only, so the screen
        // updates immediately (via the re-init below) instead of sitting stale until the round
        // trip completes. The eventual SyncStageDefinitionsPacket reply overwrites this with the
        // authoritative result.
        GraphLayoutData.set(GraphLayoutData.Snapshot.empty());
        StageManager.recomputeGraphLayout();
        history.record(Step.rearrange(before, capture(bothTrees)));

        canvas.clearSelection();
        this.minecraft.setScreen(this);
    }

    // --- Undo/redo ---------------------------------------------------------------------------

    private static Map<Boolean, TreeState> capture(Set<Boolean> trees) {
        GraphLayoutData.Snapshot snap = GraphLayoutData.get();
        Map<Boolean, TreeState> out = new HashMap<>();
        for (boolean individual : trees) {
            out.put(individual, TreeState.of(snap.isFrozen(individual), snap.tree(individual)));
        }
        return out;
    }

    /** Undo when {@code backwards}, redo otherwise. */
    private void travel(boolean backwards) {
        GraphLayoutHistory.Result result = backwards ? history.undo(applier) : history.redo(applier);
        switch (result) {
            case DONE -> {
                canvas.clearSelection();
                reloadModel();
            }
            case DISCARDED -> EditorToastHandler.show(EditorToast.Level.INFO,
                    Component.translatable("editor.historystages.graph.history.discarded.title"),
                    Component.translatable("editor.historystages.graph.history.discarded.message"));
            case NOTHING -> { }
        }
    }
}
