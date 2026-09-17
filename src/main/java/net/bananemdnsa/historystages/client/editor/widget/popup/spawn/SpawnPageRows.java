package net.bananemdnsa.historystages.client.editor.widget.popup.spawn;

import net.bananemdnsa.historystages.api.editor.widget.NumberStepper;
import net.bananemdnsa.historystages.api.editor.widget.SegmentBar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * The row vocabulary of the SpawnControl tabs, and the state that goes with it: where each row can
 * be clicked, how far the tab is scrolled, and what the cursor is resting on.
 *
 * <p>Every helper draws one row at the current cursor, records its click area, and moves the cursor
 * down — so a click is resolved against exactly what was drawn last frame, and drawing and clicking
 * cannot drift apart. The tab bodies in {@link SpawnControlPopup} do nothing but call these in
 * order.
 *
 * <p>One instance per tab. Scroll position and number fields belong to the tab that owns them, and
 * a shared instance would have the height field on one tab answering clicks meant for another.
 *
 * <p>Rows are drawn through a viewport that can be shorter than they are: on a small GUI scale the
 * dialog gives a tab less room than its content needs. Everything below works in drawn
 * coordinates, so the scroll offset needs applying in exactly one place — the cursor's start.
 */
final class SpawnPageRows {

    static final String K = "editor.historystages.spawn_control.";
    /** A row's explanation lives under the row's own key with {@code tip.} spliced in. */
    private static final String TIP = K + "tip.";
    static final int ROW_H = 18;
    private static final int BOX_S = 11;
    private static final int CELL_S = 9;
    private static final int MOON_CELL_H = 14;
    private static final int SCROLL_STEP = 12;

    /** The area a tab may draw into this frame. */
    record Frame(GuiGraphics g, Font font, int mouseX, int mouseY, int x, int y, int width, int height) {}

    private interface ClickAction {
        void click(double mouseX, double mouseY);
    }

    private record Hit(int x, int y, int w, int h, ClickAction action) {}

    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, SegmentBar.State> segmentStates = new HashMap<>();
    private final List<NumberStepper> steppers = new ArrayList<>();

    private GuiGraphics g;
    private Font font;
    private int mouseX;
    private int mouseY;
    private int left;
    private int right;
    private int cursorY;

    private int viewTop;
    private int viewHeight;
    private int scroll;
    private int contentHeight;
    private String tooltip;

    NumberStepper stepper(int min, int max, IntConsumer onChange) {
        NumberStepper stepper = new NumberStepper(min, max, 1, min, onChange);
        steppers.add(stepper);
        return stepper;
    }

    void resetScroll() {
        scroll = 0;
    }

    void render(Frame frame, Runnable body) {
        g = frame.g();
        font = frame.font();
        mouseX = frame.mouseX();
        mouseY = frame.mouseY();
        left = frame.x() + 8;
        right = frame.x() + frame.width() - 8;
        viewTop = frame.y();
        viewHeight = frame.height();
        scroll = Math.min(scroll, maxScroll());

        int top = viewTop + 4 - scroll;
        cursorY = top;
        hits.clear();
        tooltip = null;
        // Steppers of rows not drawn this frame must not catch clicks where they were last time.
        for (NumberStepper stepper : steppers) stepper.setPosition(-10000, -10000);
        body.run();
        contentHeight = cursorY - top + 4;
        renderScrollbar();
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - viewHeight);
    }

    private void renderScrollbar() {
        int max = maxScroll();
        if (max <= 0) return;
        int trackX = right + 5;
        int barH = Math.max(12, viewHeight * viewHeight / contentHeight);
        int barY = viewTop + (viewHeight - barH) * scroll / max;
        g.fill(trackX, viewTop + 2, trackX + 2, viewTop + viewHeight - 2, 0xFF262626);
        g.fill(trackX, barY + 2, trackX + 2, barY + barH - 2, 0xFF5A5A5A);
    }

    boolean mouseClicked(double mx, double my) {
        if (!inViewport(my)) return false;
        for (NumberStepper stepper : steppers) {
            if (stepper.mouseClicked(mx, my)) return true;
        }
        for (Hit hit : List.copyOf(hits)) {
            if (mx >= hit.x() && mx < hit.x() + hit.w() && my >= hit.y() && my < hit.y() + hit.h()) {
                hit.action().click(mx, my);
                return true;
            }
        }
        return false;
    }

    boolean mouseScrolled(double mx, double my, double delta) {
        if (!inViewport(my)) return false;
        int max = maxScroll();
        if (max <= 0) return false;
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta) * SCROLL_STEP));
        return true;
    }

    boolean keyPressed(int keyCode) {
        for (NumberStepper stepper : steppers) {
            if (stepper.keyPressed(keyCode)) return true;
        }
        return false;
    }

    boolean charTyped(char c) {
        for (NumberStepper stepper : steppers) {
            if (stepper.charTyped(c)) return true;
        }
        return false;
    }

    /** Text for whatever the cursor rested on while this tab last drew, or null. */
    String tooltip() {
        return tooltip;
    }

    void commitEdits() {
        for (NumberStepper stepper : steppers) stepper.commitEdit();
    }

    static String countBadge(int count) {
        return count == 0 ? null : String.valueOf(count);
    }

    // ---- rows ----------------------------------------------------------------------

    /** Whether the cursor is on the row about to be drawn. */
    boolean rowHovered() {
        return inside(left, cursorY, right - left, ROW_H);
    }

    private void label(String key, boolean active) {
        rowTip(key);
        g.drawString(font, Component.translatable(key), left, cursorY + (ROW_H - font.lineHeight) / 2 + 1,
                active ? 0xFFCCCCCC : 0xFF666666, false);
    }

    /** A label with nothing beside it, introducing the rows below. */
    void labelRow(String key, boolean active) {
        label(key, active);
        cursorY += ROW_H;
    }

    /** Index 0 is always "Any"; the label greys out while it is chosen. */
    void segmentRow(String labelKey, List<String> optionKeys, int selected, IntConsumer onPick) {
        List<String> labels = optionKeys.stream().map(k -> Component.translatable(k).getString()).toList();
        int barW = SegmentBar.width(font, labels);
        int barX = right - barW;
        int barY = cursorY + (ROW_H - SegmentBar.height()) / 2;

        label(labelKey, selected != 0);
        SegmentBar.State state = segmentStates.computeIfAbsent(labelKey, k -> new SegmentBar.State());
        state.update(selected, SegmentBar.segmentAt(font, barX, barY, mouseX, mouseY, labels), labels.size());
        SegmentBar.draw(g, font, barX, barY, labels, selected, state, new boolean[labels.size()]);

        hits.add(new Hit(barX, barY, barW, SegmentBar.height(), (mx, my) -> {
            int index = SegmentBar.indexAt(font, barX, mx, labels);
            if (index >= 0 && index != selected) {
                playClick();
                onPick.accept(index);
            }
        }));
        cursorY += ROW_H;
    }

    void checkboxRow(String labelKey, boolean checked, boolean active, Runnable toggle) {
        int boxX = left;
        int boxY = cursorY + (ROW_H - BOX_S) / 2;
        boolean hovered = active && rowHovered();
        if (hovered) g.fill(left - 2, cursorY, right + 2, cursorY + ROW_H, 0x25FFFFFF);
        rowTip(labelKey);

        int fg = active ? 0xFFFFCC00 : 0xFF555555;
        g.fill(boxX, boxY, boxX + BOX_S, boxY + BOX_S, checked ? fg : 0xFF555555);
        g.fill(boxX + 1, boxY + 1, boxX + BOX_S - 1, boxY + BOX_S - 1, 0xFF1A1A1A);
        if (checked) g.fill(boxX + 3, boxY + 3, boxX + BOX_S - 3, boxY + BOX_S - 3, fg);
        g.drawString(font, Component.translatable(labelKey), boxX + BOX_S + 6,
                cursorY + (ROW_H - font.lineHeight) / 2 + 1,
                !active ? 0xFF555555 : hovered ? 0xFFFFFFFF : 0xFFCCCCCC, false);

        if (active) {
            hits.add(new Hit(left, cursorY, right - left, ROW_H, (mx, my) -> {
                playClick();
                toggle.run();
            }));
        }
        cursorY += ROW_H;
    }

    /** Ids as chips; clicking a chip removes it, "+" asks for more. Wraps onto further lines. */
    void chipRow(List<String> ids, Runnable onAdd) {
        int x = left + 12;
        int addW = 14;
        for (String id : List.copyOf(ids)) {
            String text = id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
            int w = font.width(text) + 8;
            if (x + w > right - addW - 4 && x > left + 12) {
                cursorY += 14;
                x = left + 12;
            }
            int y = cursorY + 3;
            boolean hovered = inside(x, y, w, 12);
            g.fill(x, y, x + w, y + 12, hovered ? 0xFF3A2626 : 0xFF262626);
            g.fill(x, y + 11, x + w, y + 12, hovered ? 0xFFAA5555 : 0xFF3A3A3A);
            g.drawString(font, text, x + 4, y + 2, id.startsWith("#") ? 0xFF8FD0FF : 0xFFCCCCCC, false);
            if (hovered) tooltip = id + "\n" + Component.translatable(TIP + "chip").getString();
            hits.add(new Hit(x, y, w, 12, (mx, my) -> {
                playClick();
                ids.remove(id);
            }));
            x += w + 4;
        }
        int bx = right - addW;
        int by = cursorY + 3;
        boolean addHovered = inside(bx, by, addW, 12);
        g.fill(bx, by, bx + addW, by + 12, addHovered ? 0x50FFCC00 : 0x25FFCC00);
        g.fill(bx, by + 11, bx + addW, by + 12, addHovered ? 0xFFFFCC00 : 0x80FFCC00);
        g.drawCenteredString(font, "+", bx + addW / 2, by + 2, 0xFFEEEEEE);
        hits.add(new Hit(bx, by, addW, 12, (mx, my) -> {
            playClick();
            onAdd.run();
        }));
        cursorY += ROW_H;
    }

    void valueRow(String labelKey, NumberStepper stepper) {
        label(labelKey, true);
        stepper.setPosition(right - NumberStepper.width(), cursorY + (ROW_H - NumberStepper.HEIGHT) / 2);
        stepper.setEnabled(true);
        stepper.render(g, font, mouseX, mouseY);
        cursorY += ROW_H;
    }

    /** @param labelKey null for a continuation row under a segment bar */
    void rangeRow(String labelKey, NumberStepper min, NumberStepper max) {
        if (labelKey != null) label(labelKey, true);
        int sw = NumberStepper.width();
        int y = cursorY + (ROW_H - NumberStepper.HEIGHT) / 2;
        int maxX = right - sw;
        int minX = maxX - 12 - sw;
        min.setPosition(minX, y);
        max.setPosition(maxX, y);
        min.setEnabled(true);
        max.setEnabled(true);
        min.render(g, font, mouseX, mouseY);
        max.render(g, font, mouseX, mouseY);
        g.drawCenteredString(font, "–", minX + sw + 6, y + 3, 0xFF888888);
        cursorY += ROW_H;
    }

    /** The eight phases as a four-by-two grid of ticks. Toggling one adds or removes it. */
    void moonGrid(Set<Integer> selected) {
        int colW = (right - left) / 4;
        for (int phase = 0; phase < 8; phase++) {
            int x = left + (phase % 4) * colW;
            int y = cursorY + 3 + (phase / 4) * MOON_CELL_H;
            int p = phase;
            String labelKey = K + "moon." + phase;
            String tip = Component.translatable(TIP + "moon.cell", phase, Component.translatable(labelKey)).getString();
            checkboxCell(x, y, labelKey, selected.contains(phase), tip, () -> {
                if (!selected.remove(p)) selected.add(p);
            });
        }
        cursorY += 2 * MOON_CELL_H + 6;
    }

    void hint(String key) {
        for (FormattedCharSequence line : font.split(Component.translatable(key), right - left)) {
            g.drawString(font, line, left, cursorY + 2, 0xFFC9A640, false);
            cursorY += font.lineHeight + 1;
        }
        cursorY += 4;
    }

    private void checkboxCell(int x, int y, String labelKey, boolean checked, String tipText, Runnable toggle) {
        g.fill(x, y, x + CELL_S, y + CELL_S, checked ? 0xFFFFCC00 : 0xFF555555);
        g.fill(x + 1, y + 1, x + CELL_S - 1, y + CELL_S - 1, 0xFF1A1A1A);
        if (checked) g.fill(x + 2, y + 2, x + CELL_S - 2, y + CELL_S - 2, 0xFFFFCC00);
        String text = Component.translatable(labelKey).getString();
        g.drawString(font, text, x + CELL_S + 3, y + 1, checked ? 0xFFFFFFFF : 0xFF999999, false);
        int w = CELL_S + 3 + font.width(text);
        if (tipText != null && inside(x, y - 2, w, CELL_S + 4)) tooltip = tipText;
        hits.add(new Hit(x, y - 2, w, CELL_S + 4, (mx, my) -> {
            playClick();
            toggle.run();
        }));
    }

    private boolean inside(int x, int y, int w, int h) {
        return inViewport(mouseY) && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private boolean inViewport(double y) {
        return y >= viewTop && y < viewTop + viewHeight;
    }

    /**
     * Shows the row's explanation while the cursor is on it. Rows without a {@code tip.} key get
     * none, which is how a row that needs no explaining opts out — by saying nothing in the lang
     * file rather than by passing a null around.
     */
    private void rowTip(String labelKey) {
        if (!labelKey.startsWith(K) || !rowHovered()) return;
        String key = TIP + labelKey.substring(K.length());
        if (Language.getInstance().has(key)) tooltip = Component.translatable(key).getString();
    }

    static void addOnce(List<String> ids, String id) {
        if (!ids.contains(id)) ids.add(id);
    }

    static void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
