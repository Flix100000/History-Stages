package net.bananemdnsa.historystages.client.editor.widget.popup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import net.bananemdnsa.historystages.api.editor.widget.PickerOverlay;
import net.bananemdnsa.historystages.api.editor.widget.SearchBar;
import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.bananemdnsa.historystages.client.editor.tab.ItemTypeSelection;
import net.bananemdnsa.historystages.client.editor.widget.SearchPanelChrome;
import net.bananemdnsa.historystages.data.lock.ItemTypeChoices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * "Applies to" for an enchantment or effect entry: every item type it can be found on, ticked
 * while locked.
 *
 * <p>The left half of {@link IngredientUsagePopup} — same header, search bar, rows, checkboxes
 * and footer — so the two read as one family. Ticked means locked; on Done the unticked types
 * are handed back as the entry's spared types.
 */
public class ItemTypesPopup implements PickerOverlay {

    private static final int PADDING = 8;
    private static final int ROW_HEIGHT = 18;
    private static final int VISIBLE_ROWS = 9;
    private static final int LIST_W = 240;
    private static final int SCROLLBAR_W = 4;
    private static final int CHECKBOX_SIZE = 12;
    private static final int HEADER_HEIGHT = 22;
    private static final int FOOTER_HEIGHT = 30;
    private static final int BTN_H = 20;
    private static final int CANCEL_W = 70;
    private static final int DONE_W = 70;

    /** One row: the item type, how it is drawn, and what the search matches against. */
    private record Entry(String id, ItemStack stack, String name, String namespace, String search) {
    }

    private final String subjectName;
    private final List<Entry> entries = new ArrayList<>();
    private final List<String> allIds;
    private final List<String> previouslySpared;
    private final Set<String> ticked = new HashSet<>();
    private final Consumer<List<String>> onSpared;
    private final SearchBar searchBar;
    private final List<Entry> visibleRows = new ArrayList<>();

    private final Anim cancelHover = new Anim();
    private final Anim doneHover = new Anim();
    private final Map<String, Anim> checkboxHover = new HashMap<>();

    private boolean visible = false;
    private int panelX, panelY, panelW, panelH;
    private int scrollRow = 0;
    private boolean draggingScrollbar = false;

    public ItemTypesPopup(String subjectName, List<String> types, List<String> spared,
                          Consumer<List<String>> onSpared) {
        this.subjectName = subjectName;
        this.allIds = new ArrayList<>(types);
        this.previouslySpared = new ArrayList<>(spared);
        this.onSpared = onSpared;
        this.searchBar = SearchPanelChrome.createSearchBar(
                Component.translatable("editor.historystages.ingredient_usage.search").getString(),
                text -> rebuildRows(), null);
        // One group, so the two exclude each other like vanilla/modded do.
        searchBar.filters().addOption("only_ticked",
                Component.translatable("editor.historystages.search.filter.ticked").getString(), "ticked");
        searchBar.filters().addOption("only_unticked",
                Component.translatable("editor.historystages.search.filter.unticked").getString(), "ticked");
        for (String id : types) {
            ResourceLocation key = ResourceLocation.tryParse(id);
            ItemStack stack = key != null ? new ItemStack(BuiltInRegistries.ITEM.get(key)) : ItemStack.EMPTY;
            String name = ItemTypeChoices.displayName(id);
            String namespace = key != null ? key.getNamespace() : "";
            entries.add(new Entry(id, stack, name, namespace, (name + " " + id).toLowerCase()));
        }
    }

    @Override
    public void show(int centerX, int centerY, int parentWidth) {
        ticked.clear();
        ticked.addAll(ItemTypeSelection.locked(allIds, previouslySpared));
        scrollRow = 0;
        checkboxHover.clear();
        cancelHover.set(0.0f);
        doneHover.set(0.0f);
        panelW = PADDING + LIST_W + SCROLLBAR_W + 2 + PADDING;
        panelH = HEADER_HEIGHT + SearchBar.HEIGHT + 6 + VISIBLE_ROWS * ROW_HEIGHT + FOOTER_HEIGHT;
        panelX = Math.max(4, centerX - panelW / 2);
        panelY = Math.max(4, centerY - panelH / 2);
        searchBar.setPosition(panelX + PADDING, searchY(), panelW - PADDING * 2);
        searchBar.setText("");
        searchBar.setFocused(false);
        rebuildRows();
        visible = true;
    }

    @Override public void hide() { visible = false; }
    @Override public boolean isVisible() { return visible; }
    @Override public void setFilter(String filter) { searchBar.setText(filter); }

    // ========== LAYOUT ==========

    private int searchY() { return panelY + HEADER_HEIGHT; }
    private int listY() { return searchY() + SearchBar.HEIGHT + 6; }
    private int listX() { return panelX + PADDING; }
    private int listH() { return VISIBLE_ROWS * ROW_HEIGHT; }
    private int footerY() { return panelY + panelH - FOOTER_HEIGHT; }
    private int rowCbX() { return listX() + LIST_W - CHECKBOX_SIZE - 3; }
    private int allCbX() { return panelX + panelW - PADDING - CHECKBOX_SIZE; }
    private int doneX() { return panelX + panelW - PADDING - DONE_W; }
    private int cancelX() { return doneX() - 6 - CANCEL_W; }
    private int maxScrollRow() { return Math.max(0, visibleRows.size() - VISIBLE_ROWS); }

    // ========== DATA ==========

    private void rebuildRows() {
        visibleRows.clear();
        String query = searchBar.getText().trim().toLowerCase();
        boolean onlyVanilla = searchBar.filters().isActive("only_vanilla");
        boolean onlyModded = searchBar.filters().isActive("only_modded");
        boolean onlyTicked = searchBar.filters().isActive("only_ticked");
        boolean onlyUnticked = searchBar.filters().isActive("only_unticked");
        // Read when the filter or search changes, not on every click: a row unticked under the
        // "only ticked" filter stays put until the next change, instead of vanishing under the cursor.
        for (Entry e : entries) {
            boolean vanilla = "minecraft".equals(e.namespace());
            if (onlyVanilla && !vanilla) continue;
            if (onlyModded && vanilla) continue;
            if (onlyTicked && !ticked.contains(e.id())) continue;
            if (onlyUnticked && ticked.contains(e.id())) continue;
            if (!query.isEmpty()) {
                boolean match = query.startsWith("@")
                        ? e.namespace().startsWith(query.substring(1))
                        : e.search().contains(query);
                if (!match) continue;
            }
            visibleRows.add(e);
        }
        scrollRow = Math.min(scrollRow, maxScrollRow());
    }

    private boolean allVisibleTicked() {
        if (visibleRows.isEmpty()) return false;
        for (Entry e : visibleRows) if (!ticked.contains(e.id())) return false;
        return true;
    }

    // ========== RENDER ==========

    @Override
    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (!visible) return;

        g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0x88000000);
        g.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF3D3D3D);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF1A1A1A);

        renderHeader(g, font, mouseX, mouseY);
        renderList(g, font, mouseX, mouseY);
        renderFooter(g, font, mouseX, mouseY);
        // Last, so its dropdown lies over the rows.
        searchBar.render(g, font, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphics g, Font font, int mouseX, int mouseY) {
        String title = Component.translatable("editor.historystages.item_types.title_for",
                subjectName, entries.size()).getString();
        String allLabel = Component.translatable("editor.historystages.ingredient_usage.all").getString();
        int cbX = allCbX();
        int titleMax = cbX - font.width(allLabel) - 12 - (panelX + PADDING);
        if (font.width(title) > titleMax) title = font.plainSubstrByWidth(title, titleMax - 8) + "...";
        g.drawString(font, title, panelX + PADDING, panelY + 7, 0xFFFFFF, false);
        g.drawString(font, allLabel, cbX - font.width(allLabel) - 5, panelY + 7, 0x999999, false);
        renderCheckbox(g, cbX, panelY + 5, allVisibleTicked(), mouseX, mouseY, "all");
    }

    private void renderList(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int x = listX();
        int y = listY();
        if (visibleRows.isEmpty()) {
            String text = Component.translatable("editor.historystages.ingredient_usage.no_match").getString();
            g.drawString(font, text, x + (LIST_W - font.width(text)) / 2, y + listH() / 2 - 4, 0x777777, false);
            return;
        }

        boolean overFilter = searchBar.isMouseOverFilterUi(mouseX, mouseY);
        g.enableScissor(x, y, x + LIST_W, y + listH());
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scrollRow + i;
            if (index >= visibleRows.size()) break;
            Entry e = visibleRows.get(index);
            int rowY = y + i * ROW_HEIGHT;
            boolean rowHovered = !overFilter && isIn(mouseX, mouseY, x, rowY, LIST_W, ROW_HEIGHT);
            boolean locked = ticked.contains(e.id());

            g.fill(x, rowY, x + LIST_W, rowY + ROW_HEIGHT - 1, rowHovered ? 0xFF353535 : 0xFF252525);
            if (rowHovered) g.fill(x, rowY, x + 2, rowY + ROW_HEIGHT - 1, 0xFFFFCC00);

            g.renderItem(e.stack(), x + 4, rowY + 1);
            int nsW = font.width(e.namespace());
            int textX = x + 24;
            int textMax = rowCbX() - 6 - nsW - 6 - textX;
            String name = e.name();
            if (font.width(name) > textMax) name = font.plainSubstrByWidth(name, textMax - 6) + "...";
            g.drawString(font, name, textX, rowY + 5, locked ? 0xDDDDDD : 0x888888, false);
            g.drawString(font, e.namespace(), rowCbX() - 6 - nsW, rowY + 5, 0x666666, false);
            renderCheckbox(g, rowCbX(), rowY + (ROW_HEIGHT - CHECKBOX_SIZE) / 2, locked, mouseX, mouseY, e.id());
        }
        g.disableScissor();

        int max = maxScrollRow();
        if (max > 0) {
            int barX = x + LIST_W + 2;
            g.fill(barX, y, barX + SCROLLBAR_W, y + listH(), 0xFF252525);
            int thumbH = Math.max(10, (int) ((float) VISIBLE_ROWS / visibleRows.size() * listH()));
            int thumbY = y + (int) ((float) scrollRow / max * (listH() - thumbH));
            g.fill(barX, thumbY, barX + SCROLLBAR_W, thumbY + thumbH, 0xFF888888);
        }
    }

    private void renderFooter(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int fy = footerY() + 5;
        String note = Component.translatable("editor.historystages.item_types.count",
                ticked.size(), entries.size()).getString();
        g.drawString(font, note, panelX + PADDING, fy + 6, 0x888888, false);

        boolean overCancel = isIn(mouseX, mouseY, cancelX(), fy, CANCEL_W, BTN_H);
        boolean overDone = isIn(mouseX, mouseY, doneX(), fy, DONE_W, BTN_H);
        renderButton(g, font, Component.translatable("editor.historystages.cancel").getString(), cancelX(), fy,
                CANCEL_W, Ease.outCubic(cancelHover.ramp(overCancel, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS)));
        renderButton(g, font, Component.translatable("editor.historystages.lock_actions.btn_done").getString(),
                doneX(), fy, DONE_W, Ease.outCubic(doneHover.ramp(overDone, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS)));
    }

    private void renderCheckbox(GuiGraphics g, int x, int y, boolean checked, int mx, int my, String hoverKey) {
        boolean over = mx >= x && mx < x + CHECKBOX_SIZE && my >= y && my < y + CHECKBOX_SIZE;
        float hp = Ease.outCubic(checkboxHover.computeIfAbsent(hoverKey, k -> new Anim())
                .ramp(over, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
        float progress = checked ? 1.0f : hp;
        int bgAlpha = (int) (0x30 + progress * 0x20);
        int bgG = (int) (0xFF - progress * 0x33);
        int bgB = (int) (0xFF - progress * 0xFF);
        g.fill(x, y, x + CHECKBOX_SIZE, y + CHECKBOX_SIZE, (bgAlpha << 24) | (0xFF << 16) | (bgG << 8) | bgB);
        int accentAlpha = (int) (0x60 + progress * 0x9F);
        g.fill(x, y + CHECKBOX_SIZE - 1, x + CHECKBOX_SIZE, y + CHECKBOX_SIZE, (accentAlpha << 24) | 0xFFCC00);
        g.fill(x, y, x + CHECKBOX_SIZE, y + 1, 0x20FFFFFF);
        g.fill(x, y, x + 1, y + CHECKBOX_SIZE, 0x15FFFFFF);
        g.fill(x + CHECKBOX_SIZE - 1, y, x + CHECKBOX_SIZE, y + CHECKBOX_SIZE, 0x15FFFFFF);
        if (checked) {
            int color = 0xFFDDDDDD;
            g.fill(x + 2, y + 5, x + 4, y + 7, color);
            g.fill(x + 3, y + 6, x + 5, y + 8, color);
            g.fill(x + 4, y + 7, x + 6, y + 9, color);
            g.fill(x + 5, y + 6, x + 7, y + 8, color);
            g.fill(x + 6, y + 5, x + 8, y + 7, color);
            g.fill(x + 7, y + 4, x + 9, y + 6, color);
            g.fill(x + 8, y + 3, x + 10, y + 5, color);
        }
    }

    private void renderButton(GuiGraphics g, Font font, String text, int x, int y, int w, float hp) {
        int bgAlpha = (int) (0x30 + hp * 0x20);
        int bgG = (int) (0xFF - hp * 0x33);
        int bgB = (int) (0xFF - hp * 0xFF);
        g.fill(x, y, x + w, y + BTN_H, (bgAlpha << 24) | (0xFF << 16) | (bgG << 8) | bgB);
        int accentAlpha = (int) (0x60 + hp * 0x9F);
        g.fill(x, y + BTN_H - 2, x + w, y + BTN_H, (accentAlpha << 24) | 0xFFCC00);
        g.fill(x, y, x + w, y + 1, 0x20FFFFFF);
        g.fill(x, y, x + 1, y + BTN_H, 0x15FFFFFF);
        g.fill(x + w - 1, y, x + w, y + BTN_H, 0x15FFFFFF);
        int gray = (int) (0xCC + hp * 0x33);
        g.drawString(font, text, x + (w - font.width(text)) / 2, y + (BTN_H - 8) / 2,
                (0xFF << 24) | (gray << 16) | (gray << 8) | gray, false);
    }

    private static boolean isIn(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ========== INPUT ==========

    @Override
    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!visible) return false;
        // The filter dropdown hangs below the bar and may reach past the panel.
        if (searchBar.mouseClicked(mouseX, mouseY)) return true;
        if (!isIn(mouseX, mouseY, panelX, panelY, panelW, panelH)) {
            hide();
            return true;
        }
        searchBar.setFocused(false);

        if (isIn(mouseX, mouseY, allCbX(), panelY + 5, CHECKBOX_SIZE, CHECKBOX_SIZE)) {
            click();
            boolean on = !allVisibleTicked();
            for (Entry e : visibleRows) {
                if (on) ticked.add(e.id()); else ticked.remove(e.id());
            }
            return true;
        }

        int fy = footerY() + 5;
        if (isIn(mouseX, mouseY, cancelX(), fy, CANCEL_W, BTN_H)) {
            click();
            hide();
            return true;
        }
        if (isIn(mouseX, mouseY, doneX(), fy, DONE_W, BTN_H)) {
            click();
            hide();
            onSpared.accept(ItemTypeSelection.spared(allIds, ticked, previouslySpared));
            return true;
        }

        if (maxScrollRow() > 0 && isIn(mouseX, mouseY, listX() + LIST_W, listY(), SCROLLBAR_W + 4, listH())) {
            draggingScrollbar = true;
            scrollFromMouse(mouseY);
            return true;
        }

        if (isIn(mouseX, mouseY, listX(), listY(), LIST_W, listH())) {
            int index = scrollRow + (int) ((mouseY - listY()) / ROW_HEIGHT);
            if (index >= 0 && index < visibleRows.size()) {
                click();
                String id = visibleRows.get(index).id();
                if (!ticked.remove(id)) ticked.add(id);
            }
        }
        return true;
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY) {
        if (!visible || !draggingScrollbar) return false;
        scrollFromMouse(mouseY);
        return true;
    }

    @Override
    public boolean mouseReleased() {
        if (draggingScrollbar) { draggingScrollbar = false; return true; }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!visible) return false;
        scrollRow = Math.max(0, Math.min(maxScrollRow(), scrollRow - (int) scrollY));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode) {
        if (!visible) return false;
        if (keyCode == 256) { // ESC: leave the search first, then the popup
            if (searchBar.isFocused()) { searchBar.setFocused(false); return true; }
            hide();
            return true;
        }
        searchBar.keyPressed(keyCode);
        return true;
    }

    @Override
    public boolean charTyped(char c) {
        if (!visible) return false;
        searchBar.charTyped(c);
        return true;
    }

    private void scrollFromMouse(double mouseY) {
        int max = maxScrollRow();
        int thumbH = Math.max(10, (int) ((float) VISIBLE_ROWS / Math.max(1, visibleRows.size()) * listH()));
        float usable = listH() - thumbH;
        if (usable <= 0) return;
        float ratio = (float) (mouseY - listY() - thumbH / 2.0) / usable;
        scrollRow = Math.round(Math.max(0, Math.min(1, ratio)) * max);
    }
}
