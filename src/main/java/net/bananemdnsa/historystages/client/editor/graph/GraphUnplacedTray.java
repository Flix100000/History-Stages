package net.bananemdnsa.historystages.client.editor.graph;

import net.bananemdnsa.historystages.client.editor.widget.MarqueeText;
import net.bananemdnsa.historystages.client.editor.widget.Scrollbar;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The "Unplaced" section docked at the bottom of {@link GraphSidebar}: stages a frozen tree has no
 * position for. Editor only.
 *
 * <p>It is also where stages are dropped to take them off the map, so it stays on screen as a
 * bare header when it is empty. It scrolls on its own and does not move with the stage list above
 * it, so it is always in the same place when an author goes looking for it.
 */
final class GraphUnplacedTray {

    static final int HEADER_H = 16;
    private static final int ROW_H = 16;
    /** Share of the sidebar the tray may take before it scrolls instead of growing. */
    private static final float MAX_SHARE = 0.4f;
    private static final int ROW_LEFT_PAD = 6;
    private static final int ICON_SIZE = 12;

    private static final int ACCENT = 0xFFFFCC00;
    private static final int BG = 0xFF141416;
    private static final int HEADER_TEXT = 0xFF999999;
    private static final int COUNT_TEXT = 0xFF666666;
    private static final int ROW_TEXT = 0xFFDDDDDD;
    private static final int ROW_HOVER_FILL = 0x2AFFCC00;
    private static final int TAG_TEXT = 0xFF888888;

    private final Scrollbar scrollbar = new Scrollbar();
    private final Map<String, ItemStack> icons = new HashMap<>();

    private List<StageGraphModel.Node> rows = List.of();
    private boolean expanded = true;
    private float scroll;
    private float maxScroll;
    private int x, y, w, h;

    /** Takes the model's unplaced stages, filtered by the sidebar's search text. */
    void setRows(List<StageGraphModel.Node> unplaced, String query) {
        List<StageGraphModel.Node> out = new ArrayList<>();
        for (StageGraphModel.Node node : unplaced) {
            if (query.isEmpty()
                    || node.stageId().toLowerCase(Locale.ROOT).contains(query)
                    || label(node).toLowerCase(Locale.ROOT).contains(query)) {
                out.add(node);
            }
        }
        out.sort(Comparator.comparing(StageGraphModel.Node::individual)
                .thenComparing(n -> label(n).toLowerCase(Locale.ROOT))
                .thenComparing(StageGraphModel.Node::stageId));
        rows = out;
        clampScroll();
    }

    /** Height this tray wants inside a sidebar of {@code sidebarHeight}. */
    int preferredHeight(int sidebarHeight) {
        if (!expanded || rows.isEmpty()) return HEADER_H;
        int max = Math.max(HEADER_H + ROW_H, Math.round(sidebarHeight * MAX_SHARE));
        return Math.min(max, HEADER_H + rows.size() * ROW_H + 4);
    }

    void setBounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        clampScroll();
    }

    private void clampScroll() {
        int body = Math.max(0, h - HEADER_H);
        maxScroll = expanded ? Math.max(0, rows.size() * ROW_H + 4 - body) : 0;
        scroll = Math.max(0, Math.min(scroll, maxScroll));
    }

    /** {@code dragging} is the stage currently being pulled out of the tray, drawn at the cursor instead. */
    void render(GuiGraphics g, Font font, int mouseX, int mouseY, StageGraphModel.Node dragging) {
        if (w <= 0 || h <= 0) return;
        g.fill(x, y, x + w, y + h, BG);
        g.fill(x, y, x + w, y + 1, ACCENT);

        String chevron = expanded ? "▼ " : "▶ ";
        String count = String.valueOf(rows.size());
        int textY = y + 4;
        int countX = x + w - Scrollbar.WIDTH - 2 - font.width(count);
        g.drawString(font, count, countX, textY, COUNT_TEXT, false);
        String title = chevron + Component.translatable("editor.historystages.graph.unplaced").getString();
        g.drawString(font, MarqueeText.truncate(font, title, countX - 6 - (x + ROW_LEFT_PAD)),
                x + ROW_LEFT_PAD, textY, HEADER_TEXT, false);

        if (!expanded || rows.isEmpty()) return;

        int top = y + HEADER_H;
        int bottom = y + h;
        String tag = Component.translatable("editor.historystages.graph.unplaced.individual_tag").getString();
        int scrollPx = Math.round(scroll);

        g.enableScissor(x, top, x + w, bottom);
        for (int i = 0; i < rows.size(); i++) {
            int rowTop = top - scrollPx + i * ROW_H;
            if (rowTop + ROW_H < top || rowTop > bottom) continue;
            StageGraphModel.Node node = rows.get(i);
            if (node == dragging) continue;

            boolean hovered = mouseX >= x && mouseX < x + w
                    && mouseY >= Math.max(rowTop, top) && mouseY < Math.min(rowTop + ROW_H, bottom);
            if (hovered) g.fill(x, rowTop, x + w, rowTop + ROW_H, ROW_HOVER_FILL);

            int left = x + ROW_LEFT_PAD;
            drawIcon(g, node, left, rowTop + 2);
            int textLeft = left + ICON_SIZE + 4;
            int textRight = x + w - Scrollbar.WIDTH - 2;
            if (node.individual()) {
                int tagX = textRight - font.width(tag);
                g.drawString(font, tag, tagX, rowTop + 4, TAG_TEXT, false);
                textRight = tagX - 4;
            }
            g.drawString(font, MarqueeText.truncate(font, label(node), Math.max(0, textRight - textLeft)),
                    textLeft, rowTop + 4, hovered ? ACCENT : ROW_TEXT, false);
        }
        g.disableScissor();

        scrollbar.render(g, x + w - Scrollbar.WIDTH - 1, top, bottom, scroll, maxScroll, mouseX, mouseY);
    }

    private void drawIcon(GuiGraphics g, StageGraphModel.Node node, int left, int top) {
        if (node.icon() == null || node.icon().isEmpty()) return;
        ItemStack stack = icons.computeIfAbsent(node.icon(), key -> {
            ResourceLocation rl = ResourceLocation.tryParse(key);
            if (rl == null) return ItemStack.EMPTY;
            Item item = BuiltInRegistries.ITEM.get(rl);
            return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        });
        if (stack.isEmpty()) return;
        g.pose().pushPose();
        g.pose().translate(left, top, 0);
        g.pose().scale(ICON_SIZE / 16f, ICON_SIZE / 16f, 1f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** True when the press hit the header, which folds the tray open or shut. */
    boolean clickHeader(double mx, double my) {
        if (!contains(mx, my) || my >= y + HEADER_H) return false;
        expanded = !expanded;
        clampScroll();
        return true;
    }

    /** The stage under the point, or null for the header, empty space or outside. */
    StageGraphModel.Node rowAt(double mx, double my) {
        if (!expanded || !contains(mx, my) || my < y + HEADER_H) return null;
        int index = (int) ((my - y - HEADER_H + scroll) / ROW_H);
        return index >= 0 && index < rows.size() ? rows.get(index) : null;
    }

    boolean scrollbarClicked(double mx, double my) {
        if (!scrollbar.mouseClicked(mx, my)) return false;
        scroll = scrollbar.scrollFor(my);
        return true;
    }

    boolean scrollbarDragged(double my) {
        if (!scrollbar.isDragging()) return false;
        scroll = scrollbar.scrollFor(my);
        return true;
    }

    void mouseReleased() {
        scrollbar.mouseReleased();
    }

    boolean mouseScrolled(double mx, double my, double delta) {
        if (!contains(mx, my) || maxScroll <= 0) return false;
        scroll = (float) Math.max(0, Math.min(maxScroll, scroll - delta * ROW_H));
        return true;
    }

    private static String label(StageGraphModel.Node node) {
        return node.label() == null || node.label().isEmpty() ? node.stageId() : node.label();
    }
}
