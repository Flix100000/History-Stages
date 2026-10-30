package net.bananemdnsa.historystages.client.editor.graph;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The shortcut overview on the stage graph, docked under the "?" button. It stays open while the
 * author keeps working, so the keys can be tried while reading them; only clicks on the panel
 * itself are swallowed.
 *
 * <p>Same material as the editor's panels ({@code StageDetailScreen}): dark body, grey frame,
 * gold top edge.
 */
public final class GraphShortcutPanel {

    private static final String[] EVERYWHERE = {"search", "fit", "zoom", "zoom_reset", "help", "esc"};
    private static final String[] EDITOR_ONLY = {"undo", "redo", "select", "remove"};
    private static final String[] EDITOR_MOUSE = {"drag", "ctrl_click", "ctrl_drag"};

    private static final int BODY = 0xFF1A1A1A;
    private static final int FRAME = 0xFF333333;
    private static final int GOLD = 0xFFFFCC00;
    private static final int SECTION_TEXT = 0xFF888888;
    private static final int SECTION_RULE = 0xFF555555;
    private static final int KEY_BG = 0xFF0D0D0D;
    private static final int KEY_FRAME = 0xFF444444;
    private static final int KEY_TEXT = 0xFFFFFFFF;
    private static final int ACTION_TEXT = 0xFFCCCCCC;

    private static final int PAD = 6;
    private static final int ROW_H = 13;
    private static final int SECTION_H = 14;
    private static final int COLUMN_GAP = 12;
    private static final int KEY_PAD = 3;

    private record Row(String key, String action) {}

    private record Section(String title, List<Row> rows) {}

    private final List<Section> sections = new ArrayList<>();
    private int x, y, width, height;
    private int keyColumnW;

    /** Builds the content for the current mode and anchors the panel's top-right corner. */
    public void layout(Font font, int right, int top, boolean editor) {
        sections.clear();
        sections.add(section("everywhere", EVERYWHERE));
        if (editor) {
            sections.add(section("editor", EDITOR_ONLY));
            sections.add(section("mouse", EDITOR_MOUSE));
        }

        String title = Component.translatable("editor.historystages.graph.keys.title").getString();
        keyColumnW = 0;
        int actionW = font.width(title);
        int rows = 0;
        for (Section section : sections) {
            actionW = Math.max(actionW, font.width(section.title()));
            for (Row row : section.rows()) {
                keyColumnW = Math.max(keyColumnW, font.width(row.key()) + KEY_PAD * 2);
                actionW = Math.max(actionW, font.width(row.action()));
                rows++;
            }
        }
        width = PAD * 2 + keyColumnW + COLUMN_GAP + actionW;
        height = PAD + 12 + sections.size() * SECTION_H + rows * ROW_H + PAD;
        x = right - width;
        y = top;
    }

    private static Section section(String id, String[] rowIds) {
        List<Row> rows = new ArrayList<>();
        for (String rowId : rowIds) {
            rows.add(new Row(
                    Component.translatable("editor.historystages.graph.keys." + rowId + ".key").getString(),
                    Component.translatable("editor.historystages.graph.keys." + rowId).getString()));
        }
        return new Section(Component.translatable("editor.historystages.graph.keys.section." + id).getString(), rows);
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }

    public void render(GuiGraphics g, Font font) {
        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, FRAME);
        g.fill(x, y, x + width, y + height, BODY);
        g.fill(x - 1, y - 1, x + width + 1, y + 1, GOLD);

        int ty = y + PAD;
        g.drawString(font, Component.translatable("editor.historystages.graph.keys.title"), x + PAD, ty, GOLD, false);
        ty += 12;
        for (Section section : sections) {
            g.drawString(font, section.title(), x + PAD, ty + 2, SECTION_TEXT, false);
            g.fill(x + PAD, ty + 11, x + width - PAD, ty + 12, SECTION_RULE);
            ty += SECTION_H;
            for (Row row : section.rows()) {
                int kx = x + PAD;
                g.fill(kx, ty, kx + font.width(row.key()) + KEY_PAD * 2, ty + 11, KEY_FRAME);
                g.fill(kx + 1, ty + 1, kx + font.width(row.key()) + KEY_PAD * 2 - 1, ty + 10, KEY_BG);
                g.drawString(font, row.key(), kx + KEY_PAD, ty + 2, KEY_TEXT, false);
                g.drawString(font, row.action(), x + PAD + keyColumnW + COLUMN_GAP, ty + 2, ACTION_TEXT, false);
                ty += ROW_H;
            }
        }
    }
}
