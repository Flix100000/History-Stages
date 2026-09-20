package net.bananemdnsa.historystages.client.editor;

import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.bananemdnsa.historystages.client.editor.dialog.ColorInputScreen;
import net.bananemdnsa.historystages.api.editor.widget.PickerOverlay;
import net.bananemdnsa.historystages.client.editor.graph.CanvasBackgrounds;
import net.bananemdnsa.historystages.client.editor.graph.NodeShapes;
import net.bananemdnsa.historystages.client.editor.graph.NodeTextures;
import net.bananemdnsa.historystages.client.editor.graph.StageGraphConfig;
import net.bananemdnsa.historystages.client.editor.widget.ConfirmDialog;
import net.bananemdnsa.historystages.client.editor.widget.EditorTooltip;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.bananemdnsa.historystages.client.editor.widget.dropdown.EnumDropdown;
import net.bananemdnsa.historystages.client.editor.widget.list.ConfigRowList;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableTextureList;
import net.bananemdnsa.historystages.data.graph.CanvasBackgroundStyle;
import net.bananemdnsa.historystages.data.graph.GraphColors;
import net.bananemdnsa.historystages.data.graph.GraphConfigCodec;
import net.bananemdnsa.historystages.data.graph.GraphConfigEntries;
import net.bananemdnsa.historystages.data.graph.GraphKey;
import net.bananemdnsa.historystages.data.graph.GraphStageData;
import net.bananemdnsa.historystages.data.graph.NodeState;
import net.bananemdnsa.historystages.data.graph.ResolvedCanvasBackground;
import net.bananemdnsa.historystages.data.graph.ResolvedStyle;
import net.bananemdnsa.historystages.data.graph.StageStyle;
import net.bananemdnsa.historystages.data.graph.StageStyleFields;
import net.bananemdnsa.historystages.data.graph.StateStyles;
import net.bananemdnsa.historystages.client.ClientPacketHandler;
import net.bananemdnsa.historystages.network.serverbound.SaveStageGraphStylePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One stage's own node style — the {@code style} and {@code styles} blocks of
 * {@code graph_stages.json} — with a live preview of the node it produces.
 *
 * <p>Built like {@link GraphStyleScreen}, with two differences. There is one switch bar, not
 * two: the collection comes from the node that was right-clicked. And the screen owns its edit
 * buffer instead of borrowing the config editor's entries, because nothing else in this path
 * holds one.
 *
 * <p>Every row can be unset. A row showing a value it does not own is drawn dimmed by
 * {@link ConfigRowList}; a row this stage does own carries a × that puts it back.
 */
public class StageStyleScreen extends Screen {

    /** The all-states tab. Not a {@link NodeState} — it is the block that applies to all of them. */
    private static final String TAB_ALL = "all";

    /**
     * The map background this stage sets once unlocked. Not a state either, and not a node
     * style: its rows edit the {@code background} block and take their metadata from graph.toml's
     * {@code canvas} table.
     */
    private static final String TAB_BACKGROUND = "background";

    private static final List<String> TABS = List.of(TAB_ALL, "unlocked", "reachable", "locked", TAB_BACKGROUND);

    /** Extra space before the background tab, so it does not read as a fifth state. */
    private static final int BACKGROUND_TAB_GAP = 10;

    private static final List<String> BACKGROUND_LEAVES = List.of("background", "backgroundTexture", "backgroundColor");

    /** Matches {@code GraphCanvas.GRID_COLOR}. */
    private static final int PREVIEW_GRID_COLOR = 0x1FFFFFFF;
    private static final int PREVIEW_CELL = 24;

    /** Matches {@code GraphCanvas.BASE_NODE_RADIUS}, so the preview is the node at zoom 1. */
    private static final int BASE_NODE_RADIUS = 15;

    private static final int SWITCH_H = 16;
    private static final int SWITCH_GAP = 2;
    private static final int TAB_Y = 40;
    private static final int ROWS_TOP = TAB_Y + SWITCH_H + 8;
    /** Space kept clear at the bottom for the one row of buttons. */
    private static final int BUTTON_BAND = 40;
    private static final int SWITCH_BAR_MAX_W = 430;

    private final Screen parent;
    private final String stageId;
    private final boolean individual;
    private final String collection;

    /** The edit buffer: a deep copy, so cancelling really cancels. */
    private final GraphStageData.Entry buffer;
    /** What was loaded, as JSON — the cheapest honest answer to "is anything unsaved?". */
    private String initialJson;

    /** Current graph.toml values by dotted path, for working out what a row inherits. */
    private final Map<String, String> graphValues = GraphConfigCodec.collect();

    /**
     * The label column is narrower than the config tabs' 180: these rows share their width with
     * the preview, and the nine style labels are short enough that the default would push a
     * dropdown or a colour swatch past the right edge of the list.
     */
    private static final int LABEL_COLUMN_W = 130;

    private final ConfigRowList rows = new ConfigRowList();
    private String tab = TAB_ALL;

    /** Rebuilt on every tab change; the rows are a view onto {@link #buffer}, not a second copy. */
    private List<ConfigEditorScreen.ConfigEntry> currentRows = new ArrayList<>();

    private final Map<String, Anim> switchHover = new HashMap<>();
    private final Map<String, int[]> switchRects = new HashMap<>();
    private final EditorTooltip tooltip = new EditorTooltip();
    private EnumDropdown openDropdown;
    /** The texture picker, open over everything while the background texture row is edited. */
    private PickerOverlay texturePicker;

    /**
     * Bottom of the row band. Nine rows are 216px tall and do not fit above the buttons at the
     * larger GUI scales, so the rows get a scrollable band rather than running under them —
     * the same problem {@link GraphStyleScreen} solves the same way for its ten.
     */
    private int rowsBottom;
    private int maxScroll;
    private double scrollOffset;
    private final Anim smoothScroll = new Anim();
    private final Anim scrollThumbHover = new Anim();
    private boolean draggingScrollbar;
    /** Distance from the thumb's top to the cursor when the drag started. */
    private int scrollGrabOffset;

    public StageStyleScreen(Screen parent, String stageId, boolean individual) {
        super(Component.translatable("editor.historystages.graph.style.title"));
        this.parent = parent;
        this.stageId = stageId;
        this.individual = individual;
        this.collection = individual ? "individual" : "global";

        GraphStageData.Entry loaded = GraphStageData.get().tree(individual).get(stageId);
        this.buffer = loaded == null ? new GraphStageData.Entry() : loaded.copyStyles();
        if (buffer.style == null) buffer.style = new StageStyle();
        if (buffer.styles == null) buffer.styles = new StateStyles();
        if (buffer.background == null) buffer.background = new CanvasBackgroundStyle();
        this.initialJson = bufferJson();
    }

    private boolean onBackgroundTab() {
        return TAB_BACKGROUND.equals(tab);
    }

    /** What this stage itself sets for a row's leaf on the current tab, or null. */
    private String ownValue(String leaf) {
        if (!onBackgroundTab()) return StageStyleFields.get(target(), leaf);
        return switch (leaf) {
            case "background" -> buffer.background.mode;
            case "backgroundTexture" -> buffer.background.texture;
            case "backgroundColor" -> buffer.background.color;
            default -> null;
        };
    }

    private void setOwnValue(String leaf, String value) {
        if (!onBackgroundTab()) {
            StageStyleFields.set(target(), leaf, value);
            return;
        }
        switch (leaf) {
            case "background" -> buffer.background.mode = value;
            case "backgroundTexture" -> buffer.background.texture = value;
            case "backgroundColor" -> buffer.background.color = value;
            default -> { }
        }
    }

    /** graph.toml's canvas keys behind the background tab's rows, in {@link #BACKGROUND_LEAVES} order. */
    private static List<GraphKey> backgroundKeys() {
        List<GraphKey> out = new ArrayList<>();
        List<GraphKey> canvas = GraphConfigEntries.sections().getOrDefault("canvas", List.of());
        for (String leaf : BACKGROUND_LEAVES) {
            for (GraphKey key : canvas) {
                if (key.leaf().equals(leaf)) out.add(key);
            }
        }
        return out;
    }

    // --- Buffer access ------------------------------------------------------------------------

    /** The block the current tab edits. Created on demand so an untouched state stays absent. */
    private StageStyle target() {
        if (TAB_ALL.equals(tab)) return buffer.style;
        NodeState state = NodeState.valueOf(tab.toUpperCase(Locale.ROOT));
        StageStyle existing = buffer.styles.get(state);
        if (existing == null) {
            existing = new StageStyle();
            buffer.styles.set(state, existing);
        }
        return existing;
    }

    /**
     * The buffer as it would be written: the blocks that carry nothing dropped.
     *
     * <p>{@link #target()} creates a state's block the moment its tab is opened, so comparing the
     * buffer verbatim would report an unsaved change for merely looking at a tab. Pruning here
     * rather than at each call site also makes this the exact fragment the packet should carry —
     * the server drops empty blocks anyway, so sending them would only be noise.
     */
    private String bufferJson() {
        GraphStageData.Entry pruned = new GraphStageData.Entry();
        pruned.style = buffer.style == null || buffer.style.isEmpty() ? null : buffer.style.copy();
        if (buffer.styles != null && !buffer.styles.isEmpty()) {
            StateStyles states = new StateStyles();
            for (NodeState state : NodeState.values()) {
                StageStyle block = buffer.styles.get(state);
                states.set(state, block == null || block.isEmpty() ? null : block.copy());
            }
            pruned.styles = states;
        }
        pruned.background = buffer.background == null || buffer.background.isEmpty()
                ? null : buffer.background.copy();
        return GraphStageData.entryToJson(pruned);
    }

    /**
     * The value a row would show if this stage set nothing.
     *
     * <p>On a state tab that is the graph.toml block for that state with the all-states override
     * folded on top. On the all-states tab it is the graph.toml value — but only when all three
     * states agree on it, because there is no single value to name when they do not. The blocks
     * are allowed to differ and the fill colours actually do, so writing one of the three there
     * would be a quiet lie.
     *
     * @return the inherited value, or null when the three states disagree
     */
    private String inheritedValue(String leaf) {
        if (onBackgroundTab()) return graphValues.get("canvas." + leaf);
        if (TAB_ALL.equals(tab)) {
            String first = null;
            for (String state : List.of("unlocked", "reachable", "locked")) {
                String value = graphValues.get(stylePath(state, leaf));
                if (first == null) {
                    first = value;
                } else if (!first.equals(value)) {
                    return null;
                }
            }
            return first;
        }

        String base = graphValues.get(stylePath(tab, leaf));
        String allStates = StageStyleFields.get(buffer.style, leaf);
        return allStates != null ? allStates : base;
    }

    private String stylePath(String state, String leaf) {
        return GraphConfigEntries.STYLE + "." + collection + "." + state + "." + leaf;
    }

    /** The value to seed a row with when the user starts overriding it. */
    private String seedValue(GraphKey key, String leaf) {
        String inherited = inheritedValue(leaf);
        if (inherited != null) return inherited;
        // The all-states tab with three disagreeing blocks: unlocked is the one to start from.
        String unlocked = graphValues.get(stylePath("unlocked", leaf));
        return unlocked != null ? unlocked : key.defaultValue();
    }

    // --- Rows ---------------------------------------------------------------------------------

    /**
     * Builds the rows for the current tab. The metadata (kind, range, enum constants) comes from
     * the unlocked block on every node tab — the three blocks declare the same ten keys with
     * the same types, and the all-states tab has no block of its own to read. The background tab
     * reads graph.toml's canvas keys instead.
     */
    private void rebuildRows() {
        currentRows = new ArrayList<>();
        boolean background = onBackgroundTab();

        for (GraphKey key : tabKeys()) {
            String leaf = key.leaf();
            String own = ownValue(leaf);
            String inherited = inheritedValue(leaf);

            // A row with nothing to inherit still carries the value it would start from, so the
            // control has something real behind it the moment the row is clicked; what the user
            // sees until then is the hint ConfigRowList draws for a varying row.
            String shown = own != null ? own
                    : (inherited != null ? inherited : seedValue(key, leaf));

            ConfigEditorScreen.ConfigEntry entry = ConfigEditorScreen.ConfigEntry.styleRow(
                    "stagestyle." + tab + "." + leaf,
                    typeOf(key), shown, key.defaultValue(),
                    background
                            ? "editor.historystages.config.graph.canvas." + leaf
                            : "editor.historystages.config.graph.style." + leaf,
                    key.min() == null ? Double.NEGATIVE_INFINITY : key.min(),
                    key.max() == null ? Double.POSITIVE_INFINITY : key.max(),
                    key.enumConstants(), key.enumType());
            entry.inherited = own == null;
            entry.varies = own == null && inherited == null;
            currentRows.add(entry);
        }
        updateMaxScroll();
    }

    private void updateMaxScroll() {
        int contentHeight = currentRows.size() * ConfigRowList.ENTRY_HEIGHT;
        maxScroll = Math.max(0, contentHeight - (rowsBottom - ROWS_TOP));
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    /** Screen y of the row at {@code index}, with the current scroll applied. */
    private int rowTop(int index) {
        return ROWS_TOP - Math.round(smoothScroll.value()) + index * ConfigRowList.ENTRY_HEIGHT;
    }

    /** Left edge of the scrollbar track. Drawn 3px wide; the click area adds a pixel each side. */
    private int scrollbarX() {
        return contentRight() + 8;
    }

    /** Height of the scrollbar thumb. The render and the drag paths must both go through this. */
    private int thumbHeight() {
        int track = rowsBottom - ROWS_TOP;
        return Math.max(20, track * track / (maxScroll + track));
    }

    /** Top of the thumb, following the drawn scroll so thumb and rows move as one. */
    private int thumbTop() {
        int span = (rowsBottom - ROWS_TOP) - thumbHeight();
        if (maxScroll <= 0 || span <= 0) return ROWS_TOP;
        return ROWS_TOP + Math.round(smoothScroll.value() / maxScroll * span);
    }

    /**
     * Scrolls so the thumb keeps the position it was grabbed at.
     *
     * <p>Mapping the cursor straight onto the track instead would make the thumb jump to centre
     * itself on the pointer the moment it is touched, and every later pixel of the drag would be
     * off by however far from its middle it was grabbed. {@code AbstractSearchableList} carries
     * the same offset for the same reason.
     */
    private void updateScrollFromMouse(double mouseY) {
        int span = (rowsBottom - ROWS_TOP) - thumbHeight();
        if (maxScroll <= 0 || span <= 0) {
            scrollOffset = 0;
        } else {
            double top = mouseY - scrollGrabOffset - ROWS_TOP;
            float ratio = (float) Math.max(0, Math.min(1, top / span));
            scrollOffset = Math.max(0, Math.min(maxScroll, Math.round(ratio * maxScroll)));
        }
        // Snapped, not eased: while the thumb is held the list must track the cursor exactly, or
        // the thumb lags behind the pointer.
        smoothScroll.set((float) scrollOffset);
    }

    private static ConfigEditorScreen.ConfigType typeOf(GraphKey key) {
        return switch (key.kind()) {
            case BOOLEAN -> ConfigEditorScreen.ConfigType.BOOLEAN;
            case INTEGER -> ConfigEditorScreen.ConfigType.INTEGER;
            case DOUBLE -> ConfigEditorScreen.ConfigType.DOUBLE;
            case STRING -> ConfigEditorScreen.ConfigType.STRING;
            case RICH_TEXT -> ConfigEditorScreen.ConfigType.RICH_TEXT;
            case COLOR -> ConfigEditorScreen.ConfigType.COLOR;
            case ENUM -> ConfigEditorScreen.ConfigType.ENUM;
            case TEXTURE -> ConfigEditorScreen.ConfigType.TEXTURE;
        };
    }

    /** The leaf a row edits — the part of its key after the tab. */
    private static String leafOf(ConfigEditorScreen.ConfigEntry entry) {
        return entry.key.substring(entry.key.lastIndexOf('.') + 1);
    }

    /** Writes a row's value into the buffer and marks it as this stage's own. */
    private void applyRow(ConfigEditorScreen.ConfigEntry entry, String value) {
        setOwnValue(leafOf(entry), value);
        entry.value = value;
        entry.inherited = false;
        // The row now owns a value of its own, so there is nothing left for it to vary between.
        entry.varies = false;
    }

    /** Puts a row back to inheriting and re-reads what it now shows. */
    private void clearRow(ConfigEditorScreen.ConfigEntry entry) {
        String leaf = leafOf(entry);
        setOwnValue(leaf, null);
        String inherited = inheritedValue(leaf);
        entry.varies = inherited == null;
        if (inherited != null) {
            entry.value = inherited;
        } else {
            GraphKey key = keyFor(leaf);
            if (key != null) entry.value = seedValue(key, leaf);
        }
        entry.inherited = true;
    }

    // --- Lifecycle ----------------------------------------------------------------------------

    @Override
    protected void init() {
        // Before anything rebuilds the rows. A value dialog writes into entry.value and returns
        // with setScreen(this), and setScreen runs init() straight away — so a colour or a number
        // just confirmed lives only in the row objects at this moment. Rebuilding first would
        // read the buffer back over it and drop the edit entirely.
        syncRowsIntoBuffer();

        switchRects.clear();
        int total = Math.min(SWITCH_BAR_MAX_W, this.width - 40);
        int each = (total - SWITCH_GAP * (TABS.size() - 1) - BACKGROUND_TAB_GAP) / TABS.size();
        int x = this.width / 2 - total / 2;
        for (String option : TABS) {
            if (TAB_BACKGROUND.equals(option)) x += BACKGROUND_TAB_GAP;
            switchRects.put(option, new int[]{x, TAB_Y, each});
            x += each + SWITCH_GAP;
        }

        rows.setLabelColumnWidth(LABEL_COLUMN_W);
        rowsBottom = this.height - BUTTON_BAND;
        rebuildRows();

        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.back"),
                btn -> onClose(), 10, this.height - 30, 60, 20));

        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.save"),
                btn -> save(), this.width / 2 - 50, this.height - 30, 100, 20));

        // Nine × clicks per tab is the alternative, so this exists — behind a confirmation,
        // because it throws away hand-written work.
        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.graph.style.reset_all"),
                btn -> this.minecraft.setScreen(new ConfirmDialog(this,
                        Component.translatable("editor.historystages.graph.style.reset_all.title"),
                        Component.translatable("editor.historystages.graph.style.reset_all.confirm"),
                        () -> {
                            buffer.style = new StageStyle();
                            buffer.styles = new StateStyles();
                            buffer.background = new CanvasBackgroundStyle();
                            // setScreen runs init(), which rebuilds the rows on its own.
                            this.minecraft.setScreen(this);
                        })),
                this.width - 120, this.height - 30, 110, 20));
    }

    private boolean hasChanges() {
        return !initialJson.equals(bufferJson());
    }

    private void save() {
        // A value dialog may have written into a row since the last tick; without this the save
        // would send whatever the buffer happened to hold when that tick last ran.
        syncRowsIntoBuffer();

        String json = bufferJson();
        ClientPacketHandler.sendToServer(new SaveStageGraphStylePacket(stageId, individual, json));

        // Optimistic local update, as StageInfoTextScreen does it: on a dedicated server the
        // change would otherwise stay invisible until the broadcast returns. The cache has to be
        // dropped by hand here — only the sync path does it on its own, and a stale ResolvedStyle
        // would leave the node behind this screen looking untouched.
        //
        // Applied from the same JSON that went out, so what this client shows cannot differ from
        // what the server was asked to store.
        GraphStageData.set(GraphStageData.get()
                .withStyle(stageId, individual, GraphStageData.entryFromJson(json)));
        StageGraphConfig.invalidateCache();

        // Deliberately does not navigate, as the node-styles screen does not: the point of saving
        // here is to look at the preview and keep tweaking. What was just written becomes the new
        // baseline, so the unsaved marker goes out until the next edit.
        initialJson = json;
    }

    @Override
    public void onClose() {
        openDropdown = null;
        if (hasChanges()) {
            // The dialog's parent is this screen, so cancelling comes back here with the edits
            // intact; discarding is what the confirm callback does. The other way round would
            // have Cancel throw the edits away and leave Confirm with nothing to do.
            this.minecraft.setScreen(new ConfirmDialog(this,
                    Component.translatable("editor.historystages.unsaved_warning_title"),
                    Component.translatable("editor.historystages.unsaved_warning"),
                    () -> this.minecraft.setScreen(parent)));
            return;
        }
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    // --- Render -------------------------------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Own background, drawn in render() — avoids 1.21's menu blur shader, as the config
        // screen and the style screen do.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        smoothScroll.approach((float) scrollOffset, Timing.SCROLL_HALF_LIFE_MS);
        smoothScroll.settle((float) scrollOffset, 0.5f);

        g.fill(0, 0, this.width, this.height, 0xE0101010);
        g.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        ConfigEditorScreen.drawSmallText(g, stageId, this.width / 2 - this.font.width(stageId) / 4,
                24, 0xFF999999);

        renderTabs(g, mouseX, mouseY);

        String hoveredDesc = null;
        g.enableScissor(contentLeft() - 6, ROWS_TOP, contentRight() + 6, rowsBottom);
        int y = rowTop(0);
        for (ConfigEditorScreen.ConfigEntry entry : currentRows) {
            rows.renderRow(g, entry, contentLeft(), y, contentRight(), mouseX, mouseY);
            if (mouseX >= contentLeft() && mouseX <= contentRight()
                    && mouseY >= Math.max(y, ROWS_TOP)
                    && mouseY < Math.min(y + ConfigRowList.ENTRY_HEIGHT, rowsBottom)) {
                hoveredDesc = entry.descKey;
            }
            y += ConfigRowList.ENTRY_HEIGHT;
        }
        g.disableScissor();

        if (maxScroll > 0) {
            // Same helpers the press and drag paths use, so the thumb is grabbable exactly where
            // it is drawn.
            int barH = thumbHeight();
            int barY = thumbTop();
            boolean barHovered = mouseX >= scrollbarX() - 1 && mouseX <= scrollbarX() + 4
                    && mouseY >= ROWS_TOP && mouseY <= rowsBottom;
            float bh = Ease.outCubic(scrollThumbHover.ramp(barHovered || draggingScrollbar,
                    Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
            g.fill(scrollbarX(), ROWS_TOP, scrollbarX() + 3, rowsBottom, 0x20FFFFFF);
            g.fill(scrollbarX(), barY, scrollbarX() + 3, barY + barH,
                    Fade.mix(0x80FFFFFF, 0xFFFFCC00, bh));
        }

        renderPreview(g, contentRight() + 30, ROWS_TOP, rowsBottom - ROWS_TOP);

        if (hasChanges()) {
            int dotX = this.width / 2 + 55;
            float phase = (System.currentTimeMillis() % (long) Timing.BREATHE_PERIOD_MS)
                    / Timing.BREATHE_PERIOD_MS;
            g.fill(dotX, this.height - 25, dotX + 6, this.height - 19,
                    Fade.rgba(0xFFCC00, 0.4f + 0.6f * Ease.breathe(phase)));
            ConfigEditorScreen.drawSmallText(g,
                    Component.translatable("editor.historystages.unsaved").getString(),
                    dotX + 9, this.height - 24, 0xFFCC00);
        }

        super.render(g, mouseX, mouseY, partialTick);

        if (openDropdown != null) {
            openDropdown.renderButton(g, this.font, mouseX, mouseY);
            openDropdown.renderPopup(g, this.font, mouseX, mouseY);
            if (openDropdown.isExpanded()) hoveredDesc = null;
        }

        if (pickerOpen()) {
            // Lifted above the rows: text is flushed after the panel's fills, so the row labels
            // would otherwise show through it.
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.fill(0, 0, this.width, this.height, 0x80000000);
            texturePicker.render(g, this.font, mouseX, mouseY);
            g.pose().popPose();
            hoveredDesc = null;
        }

        boolean hasText = hoveredDesc != null
                && net.minecraft.client.resources.language.I18n.exists(hoveredDesc);
        tooltip.render(g, this.font, hasText ? hoveredDesc : null,
                hasText ? Component.translatable(hoveredDesc).getString() : null,
                mouseX, mouseY, this.width, this.height);
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        for (String option : TABS) {
            int[] r = switchRects.get(option);
            if (r == null) continue;
            boolean active = tab.equals(option);
            boolean hovered = mouseX >= r[0] && mouseX < r[0] + r[2]
                    && mouseY >= r[1] && mouseY < r[1] + SWITCH_H;
            float hp = Ease.outCubic(switchHover.computeIfAbsent(option, k -> new Anim())
                    .ramp(hovered && !active, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));

            g.fill(r[0], r[1], r[0] + r[2], r[1] + SWITCH_H,
                    active ? 0x40FFCC00 : Fade.mix(0x15FFFFFF, 0x25FFFFFF, hp));
            if (active) {
                g.fill(r[0], r[1] + SWITCH_H - 2, r[0] + r[2], r[1] + SWITCH_H, 0xFFFFCC00);
            }
            String label = Component.translatable(TAB_ALL.equals(option) || TAB_BACKGROUND.equals(option)
                    ? "editor.historystages.graph.style.tab." + option
                    : "editor.historystages.config.graph.style.state." + option).getString();
            g.drawCenteredString(this.font, label, r[0] + r[2] / 2, r[1] + 4,
                    active ? 0xFFFFFF : Fade.mix(0xFF999999, 0xFFDDDDDD, hp));
        }
    }

    private int contentLeft() {
        return 24;
    }

    private int contentRight() {
        return this.width / 2 + 40;
    }

    /**
     * The node as the graph would draw it, through the same {@link NodeShapes} entry points the
     * canvas uses — so a preview that disagrees with the graph would have to be a bug in the
     * drawing code rather than a second implementation of it.
     *
     * <p>The all-states tab shows all three states side by side. That tab is the one where a
     * single node would be a guess: its values land on three different graph.toml blocks, and
     * which of them the author is looking at is the whole question.
     */
    private void renderPreview(GuiGraphics g, int x, int y, int h) {
        int w = this.width - x - 24;
        if (w < 60 || h < 40) return;

        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF555555);
        g.fill(x, y, x + w, y + h, 0xFF1A1A1A);

        if (onBackgroundTab()) {
            renderBackgroundPreview(g, x, y, w, h);
            return;
        }

        List<NodeState> shown = TAB_ALL.equals(tab)
                ? List.of(NodeState.UNLOCKED, NodeState.REACHABLE, NodeState.LOCKED)
                : List.of(NodeState.valueOf(tab.toUpperCase(Locale.ROOT)));

        int cellH = h / shown.size();
        int i = 0;
        for (NodeState state : shown) {
            renderPreviewNode(g, state, x, y + i * cellH, w, cellH);
            i++;
        }
    }

    /**
     * The canvas as this stage's player would see it, with the stage's own unlocked node on it —
     * a background is only worth choosing against the node colours that will sit on top of it.
     */
    private void renderBackgroundPreview(GuiGraphics g, int x, int y, int w, int h) {
        ResolvedCanvasBackground bg = CanvasBackgrounds.withOverride(buffer.background);

        g.enableScissor(x, y, x + w, y + h);
        g.fill(x, y, x + w, y + h, 0xFF000000 | bg.rgb());
        switch (bg.mode()) {
            case "GRID" -> {
                for (int lx = x; lx < x + w; lx += PREVIEW_CELL) g.fill(lx, y, lx + 1, y + h, PREVIEW_GRID_COLOR);
                for (int ly = y; ly < y + h; ly += PREVIEW_CELL) g.fill(x, ly, x + w, ly + 1, PREVIEW_GRID_COLOR);
            }
            case "TEXTURE" -> {
                ResourceLocation tex = ResourceLocation.tryParse(bg.texture());
                if (tex != null) {
                    NodeTextures.repeatingQuad(g, tex, x, y, x + w, y + h,
                            0f, 0f, (float) w / PREVIEW_CELL, (float) h / PREVIEW_CELL);
                }
            }
            default -> { }
        }
        g.disableScissor();

        renderPreviewNode(g, NodeState.UNLOCKED, x, y, w, h);

        List<FormattedCharSequence> hint = this.font.split(
                Component.translatable("editor.historystages.graph.style.background.hint"), w - 8);
        int lineY = y + h - 4 - hint.size() * 10;
        g.fill(x, lineY - 3, x + w, y + h, 0xA0000000);
        for (FormattedCharSequence line : hint) {
            g.drawString(this.font, line, x + 4, lineY, 0xFFBBBBBB, false);
            lineY += 10;
        }
    }

    private void renderPreviewNode(GuiGraphics g, NodeState state, int x, int y, int w, int h) {
        ResolvedStyle style = previewStyle(state);

        int cx = x + w / 2;
        int cy = y + h / 2 - 6;

        // Graph-true size first; the clamp only bites in a cell too short to show it whole. The
        // label and the checkmark badge both stick out past the radius, hence the margin.
        int wanted = Math.max(3, Math.round(BASE_NODE_RADIUS * (float) style.size()));
        int room = Math.max(3, Math.min(w / 2 - 8, h / 2 - 18));
        int r = Math.min(wanted, room);

        NodeShapes.draw(g, style.shape(), cx, cy, r,
                style.fillArgb(), style.border(), Math.max(0, style.borderWidth()));
        if (style.checkmark()) {
            NodeShapes.checkmark(g, cx, cy, r, style.border());
        }

        if (!"NONE".equals(style.label())) {
            String sample = Component.translatable("ID".equals(style.label())
                    ? "editor.historystages.config.graph.style.sample_id"
                    : "editor.historystages.config.graph.style.sample_name").getString();
            g.drawCenteredString(this.font, sample, cx, cy + r + 4, style.labelColor());
        }

        if (TAB_ALL.equals(tab)) {
            // Which node is which, once there are three of them.
            ConfigEditorScreen.drawSmallText(g,
                    Component.translatable("editor.historystages.config.graph.style.state."
                            + state.name().toLowerCase(Locale.ROOT)).getString(),
                    x + 4, y + 4, 0xFF999999);
        }
    }

    /**
     * graph.toml's block for the state, with the buffer folded on top — the same two layers
     * {@code StageGraphConfig.resolve} applies, but reading the unsaved buffer instead of the
     * saved file.
     */
    private ResolvedStyle previewStyle(NodeState state) {
        String stateName = state.name().toLowerCase(Locale.ROOT);
        ResolvedStyle base = new ResolvedStyle(
                graphValue(stateName, "shape", "ROUNDED"),
                doubleValue(stateName, "size", 1.0),
                (int) doubleValue(stateName, "cornerRadius", 4),
                GraphColors.parse(graphValue(stateName, "border", "#FFFFFF"), 0xFFFFFF),
                (int) doubleValue(stateName, "borderWidth", 2),
                GraphColors.parse(graphValue(stateName, "fill", "#000000"), 0),
                doubleValue(stateName, "fillOpacity", 0.35),
                graphValue(stateName, "label", "DISPLAY_NAME"),
                GraphColors.parse(graphValue(stateName, "labelColor", "#DDDDDD"), 0xDDDDDD),
                Boolean.parseBoolean(graphValue(stateName, "checkmark", "false")));

        StageStyle override = StageStyle.overlay(buffer.style, buffer.styles.get(state));
        return ResolvedStyle.merge(base, override);
    }

    private String graphValue(String state, String leaf, String fallback) {
        String value = graphValues.get(stylePath(state, leaf));
        return value == null ? fallback : value;
    }

    private double doubleValue(String state, String leaf, double fallback) {
        try {
            return Double.parseDouble(graphValue(state, leaf, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            // A half-typed value is not worth blanking the preview over.
            return fallback;
        }
    }

    // --- Input --------------------------------------------------------------------------------

    private void openTexturePicker(ConfigEditorScreen.ConfigEntry entry) {
        SearchableTextureList picker = new SearchableTextureList(texture -> {
            applyRow(entry, texture);
            texturePicker = null;
        });
        texturePicker = picker;
        picker.show(this.width / 2, this.height / 2, this.width);
    }

    /**
     * Drops the picker once it has hidden itself: a click outside its panel hides it and still
     * reports the click as consumed, so without this the dim layer would never lift.
     */
    private boolean pickerOpen() {
        if (texturePicker != null && !texturePicker.isVisible()) texturePicker = null;
        return texturePicker != null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pickerOpen()) {
            if (!texturePicker.mouseClicked(mouseX, mouseY)) texturePicker = null;
            return true;
        }

        if (openDropdown != null) {
            boolean wasExpanded = openDropdown.isExpanded();
            if (openDropdown.mouseClicked(mouseX, mouseY)) return true;
            openDropdown = null;
            if (wasExpanded) return true;
        }

        for (Map.Entry<String, int[]> e : switchRects.entrySet()) {
            int[] r = e.getValue();
            if (mouseX < r[0] || mouseX >= r[0] + r[2]
                    || mouseY < r[1] || mouseY >= r[1] + SWITCH_H) continue;
            // rebuildRows() replaces the rows outright, so anything a value dialog wrote into
            // them since the last tick has to reach the buffer first — and target() still has to
            // mean the tab those rows belong to when it does.
            syncRowsIntoBuffer();
            tab = e.getKey();
            rebuildRows();
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        if (maxScroll > 0 && mouseX >= scrollbarX() - 1 && mouseX <= scrollbarX() + 4
                && mouseY >= ROWS_TOP && mouseY <= rowsBottom) {
            // Grabbing the thumb keeps the spot it was grabbed at; clicking the bare track has no
            // such spot, so the thumb centres itself on the cursor once and drags on from there.
            int top = thumbTop();
            int height = thumbHeight();
            scrollGrabOffset = (mouseY >= top && mouseY < top + height)
                    ? (int) (mouseY - top)
                    : height / 2;
            draggingScrollbar = true;
            updateScrollFromMouse(mouseY);
            return true;
        }

        // Outside the row band the rows are not drawn, so they must not be clickable either.
        if (mouseY < ROWS_TOP || mouseY >= rowsBottom) return false;

        int y = rowTop(0);
        for (ConfigEditorScreen.ConfigEntry entry : currentRows) {
            // The × sits in its own gutter left of the label, but the two hit tests still have to
            // be resolved in a fixed order.
            if (rows.hitTestClear(entry, contentLeft(), y, mouseX, mouseY)) {
                clearRow(entry);
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            if (rows.hitTest(entry, contentLeft(), contentRight(), y, mouseX, mouseY)) {
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                clickRow(entry, y, rows.toggleValueAt(entry, contentLeft(), mouseX));
                return true;
            }
            y += ConfigRowList.ENTRY_HEIGHT;
        }
        return false;
    }

    /**
     * Editing an inherited row is what turns it into an override, so every arm seeds the value
     * first and writes back through {@link #applyRow}.
     */
    private void clickRow(ConfigEditorScreen.ConfigEntry entry, int rowY, Boolean pickedHalf) {
        String leaf = leafOf(entry);
        if (entry.inherited) {
            GraphKey key = keyFor(leaf);
            if (key != null) entry.value = seedValue(key, leaf);
        }

        switch (entry.type) {
            // Either half writes an override, the half matching the inherited value included —
            // that is how a value gets pinned rather than merely left alone.
            case BOOLEAN -> {
                if (pickedHalf != null) applyRow(entry, String.valueOf(pickedHalf.booleanValue()));
            }
            case COLOR -> {
                applyRow(entry, entry.value);
                this.minecraft.setScreen(new ColorInputScreen(this, entry));
            }
            case ENUM -> {
                applyRow(entry, entry.value);
                EnumDropdown dropdown = new EnumDropdown(
                        entry.enumConstants, entry.value, ConfigRowList.DROPDOWN_MIN_WIDTH,
                        constant -> ConfigRowList.enumLabel(entry.enumType, constant),
                        picked -> applyRow(entry, picked));
                dropdown.setPosition(rows.controlX(entry, contentLeft()),
                        rowY + ConfigRowList.DROPDOWN_INSET_Y);
                dropdown.expand();
                openDropdown = dropdown;
            }
            case INTEGER, DOUBLE, STRING -> {
                applyRow(entry, entry.value);
                this.minecraft.setScreen(new ConfigEditorScreen.ValueInputScreen(this, entry));
            }
            // Only picking writes the override; closing the picker leaves the row as it was.
            case TEXTURE -> openTexturePicker(entry);
            default -> {
                // No other type appears in a style block.
            }
        }
    }

    private GraphKey keyFor(String leaf) {
        for (GraphKey key : tabKeys()) {
            if (key.leaf().equals(leaf)) return key;
        }
        return null;
    }

    /** The keys the current tab's rows are built from. */
    private List<GraphKey> tabKeys() {
        if (onBackgroundTab()) return backgroundKeys();
        return GraphConfigEntries.styleKeys(collection, TAB_ALL.equals(tab) ? "unlocked" : tab);
    }

    /**
     * Folds what the rows currently hold back into the buffer they are a view onto.
     *
     * <p>{@link ColorInputScreen} and {@link ConfigEditorScreen.ValueInputScreen} write straight
     * into {@code entry.value} and hand control back with no callback, so this is what keeps the
     * two in step without either dialog knowing about this screen. It has to run at every point
     * where the rows are about to be replaced or the buffer is about to be read: {@link #init()},
     * the tab switch, and {@link #save()}.
     */
    private void syncRowsIntoBuffer() {
        for (ConfigEditorScreen.ConfigEntry entry : currentRows) {
            if (!entry.inherited) setOwnValue(leafOf(entry), entry.value);
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // The picker's own scrollbar needs the drags too, or it takes the press and never moves.
        if (pickerOpen() && texturePicker.mouseDragged(mouseX, mouseY)) return true;
        if (draggingScrollbar) {
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (pickerOpen() && texturePicker.mouseReleased()) return true;
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (pickerOpen()) return texturePicker.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        // The popup is anchored to a fixed screen position, so it cannot follow its row.
        openDropdown = null;
        if (maxScroll <= 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - scrollY * 16));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (pickerOpen()) {
            if (keyCode == 256) {
                texturePicker = null;
                return true;
            }
            return texturePicker.keyPressed(keyCode);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (pickerOpen()) return texturePicker.charTyped(c);
        return super.charTyped(c, modifiers);
    }
}
