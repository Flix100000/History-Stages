package net.bananemdnsa.historystages.client.editor.widget.popup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.bananemdnsa.historystages.api.editor.widget.PickerOverlay;
import net.bananemdnsa.historystages.api.editor.widget.SearchBar;
import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.bananemdnsa.historystages.client.editor.widget.SearchPanelChrome;
import net.bananemdnsa.historystages.compat.ViewerCategoryCatalog;
import net.bananemdnsa.historystages.compat.ViewerCategoryExemptions;
import net.bananemdnsa.historystages.compat.ViewerCategorySelection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.fml.ModList;

/**
 * Picks the JEI/EMI categories the recipe lock leaves alone, by ticking them instead of typing ids.
 *
 * <p>Grouped by mod. The mod row ticks the whole mod, which is what a bare mod id in the list
 * means; while it is ticked its categories show ticked and cannot be changed one by one. JER is
 * always exempt, so its rows are ticked and dimmed. Same chrome as {@link ItemTypesPopup}.
 */
public class ViewerCategoriesPopup implements PickerOverlay {

    private static final int PADDING = 8;
    private static final int ROW_HEIGHT = 18;
    private static final int VISIBLE_ROWS = 11;
    private static final int LIST_W = 260;
    private static final int SCROLLBAR_W = 4;
    private static final int CHECKBOX_SIZE = 12;
    private static final int HEADER_HEIGHT = 22;
    private static final int FOOTER_HEIGHT = 30;
    private static final int BTN_H = 20;
    private static final int CANCEL_W = 70;
    private static final int DONE_W = 70;
    private static final int INDENT = 12;

    /** A mod row ({@code id} null) or a category row. */
    private record Row(String namespace, String id, String name, String search) {
        boolean isMod() { return id == null; }
    }

    private final List<String> configured;
    private final Consumer<List<String>> onDone;
    private final SearchBar searchBar;
    /** Every row, mods followed by their categories. */
    private final List<Row> rows = new ArrayList<>();
    private final List<Row> visibleRows = new ArrayList<>();
    private final Map<String, Anim> checkboxHover = new HashMap<>();
    private final Anim cancelHover = new Anim();
    private final Anim doneHover = new Anim();

    private ViewerCategorySelection selection;
    private boolean visible = false;
    private int panelX, panelY, panelW, panelH;
    private int scrollRow = 0;
    private boolean draggingScrollbar = false;

    public ViewerCategoriesPopup(List<String> configured, Consumer<List<String>> onDone) {
        this.configured = new ArrayList<>(configured);
        this.onDone = onDone;
        this.searchBar = SearchPanelChrome.createSearchBar(
                Component.translatable("editor.historystages.ingredient_usage.search").getString(),
                text -> rebuildRows(), null);
        Map<String, List<ViewerCategoryCatalog.Category>> byMod = new LinkedHashMap<>();
        for (ViewerCategoryCatalog.Category category : ViewerCategoryCatalog.all()) {
            byMod.computeIfAbsent(ViewerCategorySelection.namespaceOf(category.id()), k -> new ArrayList<>())
                    .add(category);
        }
        List<String> namespaces = new ArrayList<>(byMod.keySet());
        namespaces.sort((a, b) -> modName(a).compareToIgnoreCase(modName(b)));
        for (String namespace : namespaces) {
            String modName = modName(namespace);
            rows.add(new Row(namespace, null, modName, (modName + " " + namespace).toLowerCase()));
            for (ViewerCategoryCatalog.Category category : byMod.get(namespace)) {
                String name = category.title().getString();
                rows.add(new Row(namespace, category.id(), name, (name + " " + category.id()).toLowerCase()));
            }
        }
    }

    private static String modName(String namespace) {
        return ModList.get().getModContainerById(namespace)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(namespace);
    }

    @Override
    public void show(int centerX, int centerY, int parentWidth) {
        selection = new ViewerCategorySelection(configured);
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
    private int doneX() { return panelX + panelW - PADDING - DONE_W; }
    private int cancelX() { return doneX() - 6 - CANCEL_W; }
    private int maxScrollRow() { return Math.max(0, visibleRows.size() - VISIBLE_ROWS); }

    // ========== DATA ==========

    /** A mod that matches shows all its categories; otherwise only the matching ones, under their mod. */
    private void rebuildRows() {
        visibleRows.clear();
        String query = searchBar.getText().trim().toLowerCase();
        Row mod = null;
        boolean modMatched = false;
        boolean modAdded = false;
        for (Row row : rows) {
            if (row.isMod()) {
                mod = row;
                modMatched = query.isEmpty() || row.search().contains(query);
                modAdded = modMatched;
                if (modMatched) visibleRows.add(row);
                continue;
            }
            if (!modMatched && !row.search().contains(query)) continue;
            if (!modAdded && mod != null) {
                visibleRows.add(mod);
                modAdded = true;
            }
            visibleRows.add(row);
        }
        scrollRow = Math.min(scrollRow, maxScrollRow());
    }

    private static boolean alwaysExempt(Row row) {
        return ViewerCategoryExemptions.isExempt(row.namespace() + ":", List.of());
    }

    private boolean isTicked(Row row) {
        if (alwaysExempt(row)) return true;
        return row.isMod() ? selection.isWholeMod(row.namespace()) : selection.isTicked(row.id());
    }

    /** JER, and a category whose whole mod is ticked: shown ticked, not changeable here. */
    private boolean isFixed(Row row) {
        return alwaysExempt(row) || (!row.isMod() && selection.isWholeMod(row.namespace()));
    }

    // ========== RENDER ==========

    @Override
    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (!visible) return;

        g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0x88000000);
        g.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF3D3D3D);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF1A1A1A);

        g.drawString(font, Component.translatable("editor.historystages.viewer_categories.title").getString(),
                panelX + PADDING, panelY + 7, 0xFFFFFF, false);
        renderList(g, font, mouseX, mouseY);
        renderFooter(g, font, mouseX, mouseY);
        searchBar.render(g, font, mouseX, mouseY);
    }

    private void renderList(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int x = listX();
        int y = listY();
        if (visibleRows.isEmpty()) {
            String key = rows.isEmpty() ? "editor.historystages.viewer_categories.none"
                    : "editor.historystages.ingredient_usage.no_match";
            String text = Component.translatable(key).getString();
            g.drawString(font, text, x + (LIST_W - font.width(text)) / 2, y + listH() / 2 - 4, 0x777777, false);
            return;
        }

        boolean overFilter = searchBar.isMouseOverFilterUi(mouseX, mouseY);
        g.enableScissor(x, y, x + LIST_W, y + listH());
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scrollRow + i;
            if (index >= visibleRows.size()) break;
            Row row = visibleRows.get(index);
            int rowY = y + i * ROW_HEIGHT;
            boolean fixed = isFixed(row);
            boolean ticked = isTicked(row);
            boolean rowHovered = !overFilter && !fixed && isIn(mouseX, mouseY, x, rowY, LIST_W, ROW_HEIGHT);

            int bg = row.isMod() ? 0xFF2C2C2C : 0xFF252525;
            g.fill(x, rowY, x + LIST_W, rowY + ROW_HEIGHT - 1, rowHovered ? 0xFF353535 : bg);
            if (rowHovered) g.fill(x, rowY, x + 2, rowY + ROW_HEIGHT - 1, 0xFFFFCC00);

            int textX = x + 6 + (row.isMod() ? 0 : INDENT);
            String note = row.isMod()
                    ? Component.translatable(alwaysExempt(row) ? "editor.historystages.viewer_categories.always"
                            : "editor.historystages.viewer_categories.whole_mod").getString()
                    : row.id().substring(row.namespace().length() + 1);
            int noteW = font.width(note);
            int textMax = rowCbX() - 6 - noteW - 6 - textX;
            String name = row.name();
            if (font.width(name) > textMax) name = font.plainSubstrByWidth(name, textMax - 6) + "...";
            int nameColor = fixed ? 0x666666 : row.isMod() ? 0xFFCC00 : ticked ? 0xDDDDDD : 0x888888;
            g.drawString(font, name, textX, rowY + 5, nameColor, false);
            g.drawString(font, note, rowCbX() - 6 - noteW, rowY + 5, 0x666666, false);
            String hoverKey = row.isMod() ? "mod:" + row.namespace() : row.id();
            renderCheckbox(g, rowCbX(), rowY + (ROW_HEIGHT - CHECKBOX_SIZE) / 2, ticked, fixed,
                    mouseX, mouseY, hoverKey);
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
        String note = Component.translatable("editor.historystages.viewer_categories.count",
                selection.size()).getString();
        g.drawString(font, note, panelX + PADDING, fy + 6, 0x888888, false);

        boolean overCancel = isIn(mouseX, mouseY, cancelX(), fy, CANCEL_W, BTN_H);
        boolean overDone = isIn(mouseX, mouseY, doneX(), fy, DONE_W, BTN_H);
        renderButton(g, font, Component.translatable("editor.historystages.cancel").getString(), cancelX(), fy,
                CANCEL_W, Ease.outCubic(cancelHover.ramp(overCancel, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS)));
        renderButton(g, font, Component.translatable("editor.historystages.lock_actions.btn_done").getString(),
                doneX(), fy, DONE_W, Ease.outCubic(doneHover.ramp(overDone, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS)));
    }

    private void renderCheckbox(GuiGraphics g, int x, int y, boolean checked, boolean fixed,
                                int mx, int my, String hoverKey) {
        boolean over = !fixed && mx >= x && mx < x + CHECKBOX_SIZE && my >= y && my < y + CHECKBOX_SIZE;
        float hp = Ease.outCubic(checkboxHover.computeIfAbsent(hoverKey, k -> new Anim())
                .ramp(over, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
        float progress = fixed ? 0.0f : checked ? 1.0f : hp;
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
            int color = fixed ? 0xFF777777 : 0xFFDDDDDD;
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
        if (searchBar.mouseClicked(mouseX, mouseY)) return true;
        if (!isIn(mouseX, mouseY, panelX, panelY, panelW, panelH)) {
            hide();
            return true;
        }
        searchBar.setFocused(false);

        int fy = footerY() + 5;
        if (isIn(mouseX, mouseY, cancelX(), fy, CANCEL_W, BTN_H)) {
            click();
            hide();
            return true;
        }
        if (isIn(mouseX, mouseY, doneX(), fy, DONE_W, BTN_H)) {
            click();
            hide();
            onDone.accept(selection.toConfig());
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
                Row row = visibleRows.get(index);
                if (!isFixed(row)) {
                    click();
                    boolean on = !isTicked(row);
                    if (row.isMod()) selection.setWholeMod(row.namespace(), on);
                    else selection.setCategory(row.id(), on);
                }
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
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
