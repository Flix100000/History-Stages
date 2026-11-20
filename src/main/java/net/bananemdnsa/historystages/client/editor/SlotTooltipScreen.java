package net.bananemdnsa.historystages.client.editor;

import net.bananemdnsa.historystages.api.editor.widget.FormattedTextScreen;
import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.bananemdnsa.historystages.client.editor.dialog.ColorInputScreen;
import net.bananemdnsa.historystages.client.editor.widget.ConfirmDialog;
import net.bananemdnsa.historystages.client.editor.widget.EditorTooltip;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.bananemdnsa.historystages.client.slotlock.SlotHints;
import net.bananemdnsa.historystages.data.graph.GraphColors;
import net.bananemdnsa.historystages.data.tooltip.ScrollTooltipLayout;
import net.bananemdnsa.historystages.data.tooltip.ScrollTooltipLine;
import net.bananemdnsa.historystages.data.tooltip.SlotTooltipLayout;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Editor for the locked-slot tooltip: which lines show, in which order, with which text and
 * colour. A smaller sibling of {@link ScrollTooltipScreen}, built from the same parts and
 * measurements so the two read as one family: reorderable rows on the left, a live preview on
 * the right. Edits the config editor's entry in place, so the parent's Save covers it.
 */
public class SlotTooltipScreen extends Screen {

    private static final int ROWS_TOP = 30;
    private static final int BUTTON_BAND = 40;
    private static final int ROW_H = 24;
    private static final int SECTION_HEADER_H = 18;
    private static final int SECTION_GAP = 10;

    private static final int HANDLE_W = 12;
    private static final int CHECKBOX_W = 12;
    private static final int SPACER_W = 14;
    private static final int CONTROL_GAP = 3;
    private static final int LABEL_W = 118;
    private static final int FIELD_GAP = 6;
    private static final int RESET_W = 10;
    private static final int RESET_GUTTER_W = RESET_W + 4;
    private static final int SWATCH_SIZE = 14;

    private static final int PREVIEW_PAD = 8;
    private static final float PREVIEW_ZOOM_MIN = 0.5f;
    private static final float PREVIEW_ZOOM_MAX = 3.0f;
    private static final int DRAG_THRESHOLD = 3;

    private static final int MOVABLE_COUNT = SlotTooltipLayout.MOVABLE_IDS.size();
    private static final String STAGE_COLOR_ID = SlotTooltipLayout.STAGE_COLOR_ID;

    private static final Map<String, String> HINT_KEYS = Map.of(
            "title", "tooltip.historystages.slot_locked",
            "required", "tooltip.historystages.required_progress");

    private static final Map<String, String> PLACEHOLDERS = Map.of(
            "title", "%stages%",
            "hint", "%hint%",
            "required", "%stages%");

    private record RowSpec(boolean handle, boolean checkbox, boolean spacer, boolean text, boolean colorSwatch) {}

    private record PositionedRow(String id, RowSpec spec, int y) {}

    private static final Map<String, ScrollTooltipLine> DEFAULTS_BY_ID = SlotTooltipLayout.defaults()
            .stream().collect(Collectors.toMap(ScrollTooltipLine::id, l -> l));

    private final ConfigEditorScreen parent;
    private final ConfigEditorScreen.ConfigEntry entry;
    /** Always the movable lines in their current order, then the stage colour option. */
    private final List<ScrollTooltipLine> lines;

    private final Map<String, Anim> rowHover = new HashMap<>();
    private final Map<String, Anim> rowOffset = new HashMap<>();
    private final EditorTooltip tooltip = new EditorTooltip();

    private int rowsBottom;

    private float previewZoom = 1.0f;
    private float previewPanX;
    private float previewPanY;
    private boolean draggingPreview;
    private double previewDragLastX;
    private double previewDragLastY;

    private boolean dragArmed;
    private boolean dragStarted;
    private double pressX, pressY;
    private int dragFromSlot = -1;
    private int dragTargetSlot = -1;
    private String pulseId;
    private long pulseStart;

    public SlotTooltipScreen(ConfigEditorScreen parent, ConfigEditorScreen.ConfigEntry entry) {
        super(Component.translatable("editor.historystages.slottip.title"));
        this.parent = parent;
        this.entry = entry;
        this.lines = new ArrayList<>(SlotTooltipLayout.parse(List.of(entry.value.split(";", -1))));
    }

    private void writeBack() {
        entry.value = lines.stream().map(ScrollTooltipLayout::encodeLine).collect(Collectors.joining(";"));
    }

    // --- model ---

    private ScrollTooltipLine getLine(String id) {
        for (ScrollTooltipLine l : lines) if (l.id().equals(id)) return l;
        return null;
    }

    private void putLine(String id, ScrollTooltipLine updated) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).id().equals(id)) {
                lines.set(i, updated);
                break;
            }
        }
        writeBack();
    }

    private int movableSlotOf(String id) {
        for (int slot = 0; slot < MOVABLE_COUNT; slot++) if (lines.get(slot).id().equals(id)) return slot;
        return -1;
    }

    private static int indexOfId(List<ScrollTooltipLine> list, String id) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).id().equals(id)) return i;
        return -1;
    }

    private RowSpec specFor(String id) {
        if (STAGE_COLOR_ID.equals(id)) return new RowSpec(false, false, false, false, true);
        return new RowSpec(true, true, true, true, true);
    }

    /** Where a row's colour lives: the option keeps it in {@code text}, the lines in {@code style}. */
    private static String colourSource(@Nullable ScrollTooltipLine line, String id) {
        if (line == null) return "";
        return STAGE_COLOR_ID.equals(id) ? line.text() : line.style();
    }

    @Nullable
    private static String colourHex(String raw) {
        String hex = null;
        for (String token : ScrollTooltipLayout.styleTokens(raw)) {
            if (GraphColors.isValid(token)) {
                hex = GraphColors.format(GraphColors.parse(token, 0));
                continue;
            }
            ChatFormatting format = ChatFormatting.getByName(token);
            if (format != null && format.isColor() && format.getColor() != null) hex = GraphColors.format(format.getColor());
        }
        return hex;
    }

    /** Keeps bold and italic when a colour is picked; only the colour token is replaced. */
    private static String replaceColour(String style, String hex) {
        List<String> tokens = new ArrayList<>();
        if (GraphColors.isValid(hex)) tokens.add(hex);
        for (String token : ScrollTooltipLayout.styleTokens(style)) {
            if (token.equals("bold") || token.equals("italic")) tokens.add(token);
        }
        return String.join("+", tokens);
    }

    // --- sample preview ---

    private static List<Component> sample(List<ScrollTooltipLine> layout) {
        String iron = Component.translatable("editor.historystages.slottip.sample_stage").getString();
        String bronze = Component.translatable("editor.historystages.slottip.sample_stage2").getString();
        String hint = Component.translatable("editor.historystages.slottip.sample_hint").getString();
        List<SlotHints.RequiredBlock> blocks = List.of(new SlotHints.RequiredBlock(
                "tooltip.historystages.required_progress",
                bullet -> List.of(Component.literal(" • " + iron).withStyle(bullet),
                        Component.literal(" • " + bronze).withStyle(bullet))));
        return SlotHints.build(layout, hint, iron + ", " + bronze, blocks);
    }

    // --- layout ---

    @Override
    protected void init() {
        rowsBottom = this.height - BUTTON_BAND;
        this.addRenderableWidget(StyledButton.of(Component.translatable("editor.historystages.back"),
                btn -> onClose(), 10, this.height - 30, 60, 20));
        this.addRenderableWidget(StyledButton.of(Component.translatable("editor.historystages.save"),
                btn -> parent.saveConfig(), this.width / 2 - 50, this.height - 30, 100, 20));
        this.addRenderableWidget(StyledButton.of(Component.translatable("editor.historystages.reset"),
                btn -> this.minecraft.setScreen(new ConfirmDialog(this,
                        Component.translatable("editor.historystages.scrolltip.reset_all_title"),
                        Component.translatable("editor.historystages.slottip.reset_all"),
                        () -> {
                            lines.clear();
                            lines.addAll(SlotTooltipLayout.defaults());
                            writeBack();
                            previewZoom = 1.0f;
                            previewPanX = 0;
                            previewPanY = 0;
                            this.minecraft.setScreen(this);
                        })),
                this.width - 70, this.height - 30, 60, 20));
    }

    private int contentLeft() { return 24; }
    private int contentRight() { return this.width / 2 + 40; }
    private int rowContentLeft() { return contentLeft() + RESET_GUTTER_W; }

    private int textFieldX() {
        return rowContentLeft() + HANDLE_W + CONTROL_GAP + CHECKBOX_W + CONTROL_GAP + SPACER_W + CONTROL_GAP + LABEL_W;
    }

    private int swatchX() { return contentRight() - SWATCH_SIZE - 4; }

    private int textFieldWidth() {
        return Math.max(50, swatchX() - FIELD_GAP - textFieldX());
    }

    private int movableTop() { return ROWS_TOP + SECTION_HEADER_H; }

    private int optionsHeaderY() { return movableTop() + MOVABLE_COUNT * ROW_H + SECTION_GAP; }

    private int visualSlot(int originalIndex) {
        if (!dragStarted) return originalIndex;
        if (dragFromSlot < dragTargetSlot) {
            if (originalIndex > dragFromSlot && originalIndex <= dragTargetSlot) return originalIndex - 1;
        } else if (dragFromSlot > dragTargetSlot) {
            if (originalIndex >= dragTargetSlot && originalIndex < dragFromSlot) return originalIndex + 1;
        }
        return originalIndex;
    }

    private int computeDragTargetSlot(double mouseY) {
        int rel = (int) Math.floor((mouseY - movableTop() + ROW_H / 2.0) / ROW_H);
        return Math.max(0, Math.min(MOVABLE_COUNT - 1, rel));
    }

    private void advanceRowAnims() {
        for (int slot = 0; slot < MOVABLE_COUNT; slot++) {
            Anim anim = rowOffset.computeIfAbsent(lines.get(slot).id(), k -> new Anim());
            float target = dragStarted && slot != dragFromSlot ? (visualSlot(slot) - slot) * (float) ROW_H : 0f;
            anim.approach(target, Timing.SCROLL_HALF_LIFE_MS);
        }
    }

    private boolean isSettling() {
        for (int slot = 0; slot < MOVABLE_COUNT; slot++) {
            Anim a = rowOffset.get(lines.get(slot).id());
            if (a != null && !a.isAt(0f)) return true;
        }
        return false;
    }

    private List<PositionedRow> layoutRows() {
        List<PositionedRow> out = new ArrayList<>();
        int y = movableTop();
        for (int slot = 0; slot < MOVABLE_COUNT; slot++) {
            String id = lines.get(slot).id();
            if (dragStarted && slot == dragFromSlot) {
                y += ROW_H;
                continue;
            }
            float off = rowOffset.containsKey(id) ? rowOffset.get(id).value() : 0f;
            out.add(new PositionedRow(id, specFor(id), y + Math.round(off)));
            y += ROW_H;
        }
        out.add(new PositionedRow(STAGE_COLOR_ID, specFor(STAGE_COLOR_ID), optionsHeaderY() + SECTION_HEADER_H));
        return out;
    }

    private void reorderMovable(int fromSlot, int toSlot) {
        if (fromSlot == toSlot) return;
        List<ScrollTooltipLine> before = new ArrayList<>(lines.subList(0, MOVABLE_COUNT));
        List<ScrollTooltipLine> after = new ArrayList<>(before);
        after.add(toSlot, after.remove(fromSlot));
        for (int oldIndex = 0; oldIndex < before.size(); oldIndex++) {
            if (oldIndex == fromSlot) continue;
            String id = before.get(oldIndex).id();
            int newIndex = indexOfId(after, id);
            if (newIndex == oldIndex) continue;
            Anim anim = rowOffset.computeIfAbsent(id, k -> new Anim());
            anim.set(anim.value() + (oldIndex - newIndex) * (float) ROW_H);
        }
        for (int i = 0; i < after.size(); i++) lines.set(i, after.get(i));
    }

    // --- rendering ---

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Own background, drawn in render() — avoids 1.21's menu blur shader, as the config screen does.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (dragStarted) dragTargetSlot = computeDragTargetSlot(mouseY);
        advanceRowAnims();

        g.fill(0, 0, this.width, this.height, 0xE0101010);
        g.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        drawSectionHeader(g, "editor.historystages.slottip.section.lines", ROWS_TOP);
        drawSectionHeader(g, "editor.historystages.slottip.section.options", optionsHeaderY());

        String hoveredId = null;
        String hoveredControlKey = null;
        int left = contentLeft();
        int checkboxX = rowContentLeft() + HANDLE_W + CONTROL_GAP;
        int spacerX = checkboxX + CHECKBOX_W + CONTROL_GAP;
        for (PositionedRow row : layoutRows()) {
            renderRow(g, row, mouseX, mouseY);
            if (row.spec().text()) renderTextValue(g, row, mouseX, mouseY);
            if (mouseX >= left && mouseX <= contentRight() && mouseY >= row.y() && mouseY < row.y() + ROW_H) {
                hoveredId = row.id();
                RowSpec spec = row.spec();
                if (isOverridden(row.id()) && mouseX >= left + 2 && mouseX < left + 2 + RESET_W) {
                    hoveredControlKey = "editor.historystages.scrolltip.reset";
                } else if (spec.checkbox() && mouseX >= checkboxX && mouseX < checkboxX + CHECKBOX_W) {
                    hoveredControlKey = "editor.historystages.scrolltip.enabled";
                } else if (spec.spacer() && mouseX >= spacerX && mouseX < spacerX + SPACER_W) {
                    hoveredControlKey = "editor.historystages.scrolltip.spacer";
                } else if (spec.colorSwatch() && mouseX >= swatchX() && mouseX < swatchX() + SWATCH_SIZE) {
                    hoveredControlKey = "editor.historystages.scrolltip.color";
                }
            }
        }

        if (pulseId != null) {
            long age = System.currentTimeMillis() - pulseStart;
            if (age >= Timing.DROP_PULSE_MS) {
                pulseId = null;
            } else {
                for (PositionedRow row : layoutRows()) {
                    if (row.id().equals(pulseId)) {
                        drawPulseOutline(g, row.y(), Ease.pulse((float) age / Timing.DROP_PULSE_MS));
                        break;
                    }
                }
            }
        }

        renderPreview(g);
        if (dragStarted) drawGhost(g, mouseX, mouseY);

        if (parent.hasChanges()) {
            int dotX = this.width / 2 + 55;
            float phase = (System.currentTimeMillis() % (long) Timing.BREATHE_PERIOD_MS) / Timing.BREATHE_PERIOD_MS;
            g.fill(dotX, this.height - 25, dotX + 6, this.height - 19,
                    Fade.rgba(0xFFCC00, 0.4f + 0.6f * Ease.breathe(phase)));
            ConfigEditorScreen.drawSmallText(g,
                    Component.translatable("editor.historystages.unsaved").getString(), dotX + 9, this.height - 24, 0xFFCC00);
        }

        super.render(g, mouseX, mouseY, partialTick);

        String placeholders = hoveredId == null ? null : PLACEHOLDERS.get(hoveredId);
        String tipKey = hoveredControlKey != null ? hoveredControlKey : (placeholders == null ? null : hoveredId);
        String tipText = hoveredControlKey != null
                ? Component.translatable(hoveredControlKey).getString()
                : (placeholders == null ? null
                        : Component.translatable("editor.historystages.scrolltip.placeholders", placeholders).getString());
        tooltip.render(g, this.font, tipKey, tipText, mouseX, mouseY, this.width, this.height);
    }

    private void renderTextValue(GuiGraphics g, PositionedRow row, int mouseX, int mouseY) {
        int fx = textFieldX();
        int fw = textFieldWidth();
        int fy = row.y() + (ROW_H - 16) / 2;
        boolean hovered = mouseX >= fx && mouseX < fx + fw && mouseY >= row.y() && mouseY < row.y() + ROW_H;
        g.fill(fx - 1, fy - 1, fx + fw + 1, fy + 17, hovered ? 0xFFFFCC00 : 0xFF4A4A4A);
        g.fill(fx, fy, fx + fw, fy + 16, 0xFF0D0D0D);

        ScrollTooltipLine line = getLine(row.id());
        String own = line == null ? "" : line.text();
        String hintKey = HINT_KEYS.get(row.id());
        String fallback = hintKey != null ? Component.translatable(hintKey).getString()
                : "hint".equals(row.id()) ? "%hint%" : "";
        String shown = !own.isEmpty() ? own : fallback;
        int colour = own.isEmpty() ? 0xFF707070 : 0xFFFFFFFF;
        String clipped = this.font.plainSubstrByWidth(shown, fw - 8);
        if (!clipped.equals(shown)) clipped = clipped + "…";
        g.drawString(this.font, clipped, fx + 4, fy + 4, colour, false);
    }

    private void renderPreview(GuiGraphics g) {
        int x = previewX();
        g.drawString(this.font, Component.translatable("editor.historystages.scrolltip.preview").getString(),
                x, ROWS_TOP, 0xFF888888, false);
        int panelY = previewTop();
        int panelH = previewH();
        int w = previewW();
        if (w < 60 || panelH < 30) return;

        g.fill(x - 1, panelY - 1, x + w + 1, panelY + panelH + 1, 0xFF555555);
        g.fill(x, panelY, x + w, panelY + panelH, 0xFF1A1A1A);

        List<Component> components = sample(lines);
        if (components.isEmpty()) return;
        int boxW = 0;
        for (Component component : components) boxW = Math.max(boxW, this.font.width(component));
        int boxH = components.size() * (this.font.lineHeight + 1) - 1;

        float scaledW = boxW * previewZoom;
        float scaledH = boxH * previewZoom;
        previewPanX = Math.max(-scaledW + 40, Math.min(w - PREVIEW_PAD - 20, previewPanX));
        previewPanY = Math.max(-scaledH + 20, Math.min(panelH - PREVIEW_PAD - 20, previewPanY));

        g.enableScissor(x, panelY, x + w, panelY + panelH);
        g.pose().pushPose();
        g.pose().translate(x + PREVIEW_PAD + previewPanX, panelY + PREVIEW_PAD + previewPanY, 0);
        g.pose().scale(previewZoom, previewZoom, 1.0f);
        // The real thing is a vanilla tooltip, so the preview draws vanilla's own background.
        TooltipRenderUtil.renderTooltipBackground(g, 0, 0, boxW, boxH, 0);
        int ly = 0;
        for (Component component : components) {
            g.drawString(this.font, component, 0, ly, 0xFFFFFF, true);
            ly += this.font.lineHeight + 1;
        }
        g.pose().popPose();
        g.disableScissor();

        ConfigEditorScreen.drawSmallText(g,
                Component.translatable("editor.historystages.scrolltip.preview_hint").getString(),
                x, panelY + panelH + 3, 0xFF666666);
    }

    private int previewX() { return contentRight() + 30; }
    private int previewTop() { return ROWS_TOP + 14; }
    private int previewW() { return this.width - previewX() - 24; }
    private int previewH() { return rowsBottom - previewTop() - 10; }

    private boolean overPreview(double mouseX, double mouseY) {
        return mouseX >= previewX() && mouseX < previewX() + previewW()
                && mouseY >= previewTop() && mouseY < previewTop() + previewH();
    }

    private void drawSectionHeader(GuiGraphics g, String key, int y) {
        g.fill(contentLeft(), y + 13, contentRight(), y + 14, 0xFF444444);
        g.drawString(this.font, Component.translatable(key).getString(), contentLeft(), y + 2, 0xFF888888, false);
    }

    private boolean isOverridden(String id) {
        ScrollTooltipLine def = DEFAULTS_BY_ID.get(id);
        ScrollTooltipLine cur = getLine(id);
        return def != null && cur != null && !cur.equals(def);
    }

    private void renderRow(GuiGraphics g, PositionedRow row, int mouseX, int mouseY) {
        String id = row.id();
        RowSpec spec = row.spec();
        int y = row.y();
        int left = contentLeft();
        int right = contentRight();

        boolean hovered = mouseX >= left && mouseX <= right && mouseY >= y && mouseY < y + ROW_H;
        float hp = Ease.outCubic(rowHover.computeIfAbsent(id, k -> new Anim())
                .ramp(hovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
        if (hp > 0.001f) {
            g.fill(left, y, right, y + ROW_H - 2, Fade.rgba(0xFFFFFF, 0.08f * hp));
            g.fill(left, y, left + 1, y + ROW_H - 2, Fade.rgba(0xFFCC00, hp * 0.8f));
        }

        if (isOverridden(id)) {
            boolean resetHovered = mouseX >= left + 2 && mouseX < left + 2 + RESET_W && mouseY >= y && mouseY < y + ROW_H;
            g.drawString(this.font, "✕", left + 2, y + (ROW_H - 8) / 2, resetHovered ? 0xFFFF6666 : 0xFF888888, false);
        }

        ScrollTooltipLine line = getLine(id);
        int cx = rowContentLeft();
        if (spec.handle()) {
            boolean onHandle = mouseX >= cx && mouseX < cx + HANDLE_W && mouseY >= y && mouseY < y + ROW_H;
            drawHandle(g, cx + 1, y + (ROW_H - 12) / 2, onHandle);
        }
        cx += HANDLE_W + CONTROL_GAP;
        if (spec.checkbox()) drawCheckbox(g, cx, y + (ROW_H - 10) / 2, line != null && line.enabled());
        cx += CHECKBOX_W + CONTROL_GAP;
        if (spec.spacer()) drawSpacerToggle(g, cx, y + (ROW_H - 10) / 2, line != null && line.spacerBefore());
        cx += SPACER_W + CONTROL_GAP;

        String label = Component.translatable("editor.historystages.slottip.line." + id).getString();
        g.drawString(this.font, this.font.plainSubstrByWidth(label, Math.max(0, LABEL_W - 6)),
                cx, y + (ROW_H - 8) / 2, hp > 0.001f ? 0xFFFFFF : 0xDDDDDD, false);

        if (spec.colorSwatch()) {
            String hex = colourHex(colourSource(line, id));
            Integer rgb = hex != null ? GraphColors.parse(hex, 0) : null;
            int sx = swatchX();
            boolean swatchHovered = mouseX >= sx && mouseX < sx + SWATCH_SIZE && mouseY >= y && mouseY < y + ROW_H;
            int border = swatchHovered ? 0xFFFFCC00 : 0xFF555555;
            int sy = y + (ROW_H - SWATCH_SIZE) / 2;
            g.fill(sx, sy, sx + SWATCH_SIZE, sy + SWATCH_SIZE, border);
            g.fill(sx + 1, sy + 1, sx + SWATCH_SIZE - 1, sy + SWATCH_SIZE - 1, rgb != null ? (0xFF000000 | rgb) : 0xFF2A2A2A);
        }
    }

    private void drawHandle(GuiGraphics g, int x, int y, boolean active) {
        int color = active ? 0xFFEEEEEE : 0xFF888888;
        for (int col = 0; col < 2; col++) {
            for (int row = 0; row < 3; row++) {
                g.fill(x + col * 4, y + row * 4, x + col * 4 + 2, y + row * 4 + 2, color);
            }
        }
    }

    private void drawCheckbox(GuiGraphics g, int x, int y, boolean checked) {
        int border = checked ? 0xFFFFCC00 : 0xFF777777;
        g.fill(x, y, x + 10, y + 10, 0x40000000);
        g.fill(x, y, x + 10, y + 1, border);
        g.fill(x, y + 9, x + 10, y + 10, border);
        g.fill(x, y, x + 1, y + 10, border);
        g.fill(x + 9, y, x + 10, y + 10, border);
        if (checked) g.fill(x + 2, y + 2, x + 8, y + 8, 0xFFFFCC00);
    }

    private void drawSpacerToggle(GuiGraphics g, int x, int y, boolean on) {
        int color = on ? 0xFFFFCC00 : 0xFF666666;
        g.fill(x, y, x + 10, y + 2, color);
        g.fill(x, y + 6, x + 10, y + 8, color);
    }

    private void drawPulseOutline(GuiGraphics g, int y, float strength) {
        int alpha = (int) (0xFF * Math.max(0.0f, Math.min(1.0f, strength)));
        if (alpha < 4) return;
        int x1 = contentLeft() - 2, x2 = contentRight() + 2, y1 = y - 1, y2 = y + ROW_H - 1;
        int c = (alpha << 24) | 0xFFCC00;
        g.fill(x1, y1, x2, y1 + 1, c);
        g.fill(x1, y2 - 1, x2, y2, c);
        g.fill(x1, y1, x1 + 1, y2, c);
        g.fill(x2 - 1, y1, x2, y2, c);
    }

    private void drawGhost(GuiGraphics g, int mouseX, int mouseY) {
        String id = lines.get(dragFromSlot).id();
        String label = Component.translatable("editor.historystages.slottip.line." + id).getString();
        int gw = this.font.width(label) + 10;
        int gx = Math.min(mouseX + 8, this.width - gw - 4);
        int gy = Math.min(mouseY - 8, this.height - 18);
        g.pose().pushPose();
        g.pose().translate(0, 0, 410);
        g.fill(gx, gy, gx + gw, gy + 16, 0xE0101010);
        g.fill(gx, gy + 15, gx + gw, gy + 16, 0xFFFFCC00);
        g.drawString(this.font, label, gx + 5, gy + 4, 0xFFFFFF, false);
        g.pose().popPose();
    }

    // --- input ---

    private static void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void openTextEditorFor(String id) {
        ScrollTooltipLine line = getLine(id);
        if (line == null) return;
        String hintKey = HINT_KEYS.get(id);
        String placeholders = PLACEHOLDERS.get(id);
        this.minecraft.setScreen(new FormattedTextScreen(this,
                Component.translatable("editor.historystages.slottip.line." + id),
                line.text(),
                hintKey == null ? ("hint".equals(id) ? "%hint%" : "") : Component.translatable(hintKey).getString(),
                placeholders == null ? List.of() : List.of(placeholders.split(",\\s*")),
                text -> {
                    ScrollTooltipLine current = getLine(id);
                    if (current != null) putLine(id, current.withText(text));
                }));
    }

    private void openColorPickerFor(String id) {
        ScrollTooltipLine line = getLine(id);
        if (line == null) return;
        String current = colourHex(colourSource(line, id));
        String def = colourHex(colourSource(DEFAULTS_BY_ID.get(id), id));
        this.minecraft.setScreen(new ColorInputScreen(this,
                Component.translatable("editor.historystages.slottip.line." + id),
                current == null ? "" : current, def == null ? "" : def,
                hex -> {
                    ScrollTooltipLine now = getLine(id);
                    if (now == null) return;
                    putLine(id, STAGE_COLOR_ID.equals(id) ? now.withText(hex) : now.withStyle(replaceColour(now.style(), hex)));
                }));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && overPreview(mouseX, mouseY)) {
            draggingPreview = true;
            previewDragLastX = mouseX;
            previewDragLastY = mouseY;
            return true;
        }
        if (isSettling()) return false;
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        int left = contentLeft();
        int rowLeft = rowContentLeft();
        int checkboxX = rowLeft + HANDLE_W + CONTROL_GAP;
        int spacerX = checkboxX + CHECKBOX_W + CONTROL_GAP;
        for (PositionedRow row : layoutRows()) {
            if (mouseY < row.y() || mouseY >= row.y() + ROW_H || mouseX < left || mouseX > contentRight()) continue;
            RowSpec spec = row.spec();
            String id = row.id();
            if (isOverridden(id) && mouseX >= left + 2 && mouseX < left + 2 + RESET_W) {
                putLine(id, DEFAULTS_BY_ID.get(id));
                playClick();
                return true;
            }
            if (spec.handle() && mouseX >= rowLeft && mouseX < rowLeft + HANDLE_W) {
                int slot = movableSlotOf(id);
                if (slot >= 0) {
                    dragArmed = true;
                    dragStarted = false;
                    pressX = mouseX;
                    pressY = mouseY;
                    dragFromSlot = slot;
                    dragTargetSlot = slot;
                }
                return true;
            }
            ScrollTooltipLine line = getLine(id);
            if (spec.checkbox() && mouseX >= checkboxX && mouseX < checkboxX + CHECKBOX_W && line != null) {
                putLine(id, line.withEnabled(!line.enabled()));
                playClick();
                return true;
            }
            if (spec.spacer() && mouseX >= spacerX && mouseX < spacerX + SPACER_W && line != null) {
                putLine(id, line.withSpacerBefore(!line.spacerBefore()));
                playClick();
                return true;
            }
            if (spec.text() && mouseX >= textFieldX() && mouseX < textFieldX() + textFieldWidth()) {
                openTextEditorFor(id);
                return true;
            }
            if (spec.colorSwatch() && mouseX >= swatchX() && mouseX < swatchX() + SWATCH_SIZE) {
                openColorPickerFor(id);
                return true;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingPreview) {
            previewPanX += (float) (mouseX - previewDragLastX);
            previewPanY += (float) (mouseY - previewDragLastY);
            previewDragLastX = mouseX;
            previewDragLastY = mouseY;
            return true;
        }
        if (dragArmed && button == 0) {
            if (!dragStarted) {
                double dx = mouseX - pressX;
                double dy = mouseY - pressY;
                if (dx * dx + dy * dy > (double) DRAG_THRESHOLD * DRAG_THRESHOLD) dragStarted = true;
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingPreview) {
            draggingPreview = false;
            return true;
        }
        if (dragArmed && button == 0) {
            if (dragStarted) {
                int target = computeDragTargetSlot(mouseY);
                if (target != dragFromSlot) {
                    reorderMovable(dragFromSlot, target);
                    writeBack();
                    pulseId = lines.get(target).id();
                    pulseStart = System.currentTimeMillis();
                    playClick();
                }
            }
            dragArmed = false;
            dragStarted = false;
            dragFromSlot = -1;
            dragTargetSlot = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (overPreview(mouseX, mouseY)) {
            previewZoom = Math.max(PREVIEW_ZOOM_MIN, Math.min(PREVIEW_ZOOM_MAX, previewZoom + (float) scrollY * 0.1f));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
