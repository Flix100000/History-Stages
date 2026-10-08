package net.bananemdnsa.historystages.client.editor.widget.popup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.bananemdnsa.historystages.api.editor.widget.SearchBar;
import net.bananemdnsa.historystages.api.editor.widget.SegmentBar;
import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.bananemdnsa.historystages.client.editor.recipe.IngredientUsage.Kind;
import net.bananemdnsa.historystages.client.editor.recipe.IngredientUsageScan;
import net.bananemdnsa.historystages.client.editor.recipe.IngredientUsageScan.ItemRow;
import net.bananemdnsa.historystages.client.editor.recipe.IngredientUsageScan.RecipeRow;
import net.bananemdnsa.historystages.client.editor.recipe.RecipeCardLayout;
import net.bananemdnsa.historystages.client.editor.recipe.RecipeCardRenderer;
import net.bananemdnsa.historystages.client.editor.widget.EditorTooltip;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * "Recipes using this item": lists every recipe that takes one item as an ingredient and adds
 * the ticked ones as ordinary recipe entries, or their results as item entries (Issue #132).
 *
 * <p>A one-off snapshot by design. Nothing about it runs at play time, so the crafting table,
 * modded machines and the recipe viewers all see the same plain entries — the reason a runtime
 * ingredient lock was turned down (#82, #119).
 *
 * <p>Looks like {@link ModEntrySelectionPopup} on the left, with the recipe picker's cards on
 * the right for the row under the cursor.
 */
public class IngredientUsagePopup {

    private static final int PADDING = 8;
    private static final int ROW_HEIGHT = 18;
    private static final int VISIBLE_ROWS = 9;
    private static final int LIST_W = 220;
    private static final int SCROLLBAR_W = 4;
    private static final int COLUMN_GAP = 8;
    private static final int CHECKBOX_SIZE = 12;
    private static final int HEADER_HEIGHT = 22;
    private static final int FOOTER_HEIGHT = 30;
    private static final int CARD_GAP = 3;
    private static final int DETAIL_HEADER_H = 20;
    private static final int BTN_H = 20;
    private static final int CANCEL_W = 70;
    private static final int ADD_W = 90;
    private static final int MAX_ALTERNATIVES = 8;

    private static final int GROUP_LOCKED_COLOR = 0xFFFFCC00;
    private static final int GROUP_ALT_COLOR = 0xFFD9A441;

    private static final int MODE_RECIPES = 0;
    private static final int MODE_ITEMS = 1;

    /** One selectable line, whichever mode it came from. */
    private record Entry(String id, ItemStack stack, Kind kind, boolean added, String note,
                         List<RecipeRow> cards, String search) {
    }

    /** A drawn line: a group heading when {@code entry} is null. */
    private record Line(Kind group, Entry entry) {
    }

    private final Consumer<List<String>> onAddRecipes;
    private final Consumer<List<String>> onAddItems;
    private final SearchBar searchBar;
    private final SegmentBar.State modeState = new SegmentBar.State();

    private final List<List<Entry>> entriesByMode = List.of(new ArrayList<>(), new ArrayList<>());
    private final List<Set<String>> selectedByMode = List.of(new HashSet<>(), new HashSet<>());
    private final List<Line> lines = new ArrayList<>();

    private final Anim cancelHover = new Anim();
    private final Anim addHover = new Anim();
    private final Map<String, Anim> checkboxHover = new HashMap<>();

    private boolean visible = false;
    private int mode = MODE_RECIPES;
    private String clickedName = "";
    private int panelX, panelY, panelW, panelH;
    private int scrollRow = 0;
    private int detailScroll = 0;
    private boolean draggingScrollbar = false;
    /** The row the detail column shows when the cursor is not over the list. */
    private Entry pinned = null;
    private Entry hovered = null;

    public IngredientUsagePopup(Consumer<List<String>> onAddRecipes, Consumer<List<String>> onAddItems) {
        this.onAddRecipes = onAddRecipes;
        this.onAddItems = onAddItems;
        this.searchBar = new SearchBar(Component.translatable(
                "editor.historystages.ingredient_usage.search").getString())
                .onChange(text -> rebuildLines());
    }

    /**
     * Scans and opens. The added-suppliers are read once here: the popup is modal, so nothing can
     * add an entry behind its back while it is open.
     */
    public void show(String clickedId, Set<String> locked, boolean individualScope,
                     Supplier<Collection<String>> recipesAdded, Supplier<Collection<String>> itemsAdded,
                     int screenW, int screenH) {
        IngredientUsageScan.Result result = IngredientUsageScan.scan(clickedId, locked, individualScope);
        clickedName = BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(clickedId))
                .getDescription().getString();

        Set<String> haveRecipes = new HashSet<>(recipesAdded.get());
        Set<String> haveItems = new HashSet<>(itemsAdded.get());
        List<Entry> recipes = entriesByMode.get(MODE_RECIPES);
        List<Entry> items = entriesByMode.get(MODE_ITEMS);
        recipes.clear();
        items.clear();
        for (RecipeRow row : result.recipes()) {
            recipes.add(new Entry(row.recipeId(), row.result(), row.kind(),
                    haveRecipes.contains(row.recipeId()), "", List.of(row), row.searchText()));
        }
        for (ItemRow row : result.items()) {
            String note = row.recipes().size() > 1
                    ? Component.translatable("editor.historystages.ingredient_usage.recipe_count",
                            row.recipes().size()).getString()
                    : "";
            items.add(new Entry(row.itemId(), row.stack(), row.kind(),
                    haveItems.contains(row.itemId()), note, row.recipes(), row.searchText()));
        }
        for (int m = 0; m < 2; m++) {
            Set<String> selected = selectedByMode.get(m);
            selected.clear();
            for (Entry e : entriesByMode.get(m)) {
                if (!e.added() && e.kind() == Kind.ONLY_LOCKED) selected.add(e.id());
            }
        }

        mode = MODE_RECIPES;
        pinned = null;
        hovered = null;
        searchBar.setText("");
        searchBar.setFocused(false);
        checkboxHover.clear();
        cancelHover.set(0.0f);
        addHover.set(0.0f);

        panelW = PADDING + LIST_W + SCROLLBAR_W + 2 + COLUMN_GAP + detailW() + PADDING;
        panelH = HEADER_HEIGHT + SegmentBar.height() + 4 + SearchBar.HEIGHT + 6
                + VISIBLE_ROWS * ROW_HEIGHT + FOOTER_HEIGHT;
        panelX = Math.max(4, (screenW - panelW) / 2);
        panelY = Math.max(4, (screenH - panelH) / 2);
        searchBar.setPosition(panelX + PADDING, searchY(), panelW - PADDING * 2);
        rebuildLines();
        visible = true;
    }

    public void hide() { visible = false; }
    public boolean isVisible() { return visible; }

    // ========== LAYOUT ==========

    /** Wide enough for the widest card there is, as in the recipe picker. */
    private static int detailW() {
        return RecipeCardRenderer.cardWidth(RecipeCardLayout.sequence(RecipeCardLayout.MAX_COLS)) + 6;
    }

    private int segmentY() { return panelY + HEADER_HEIGHT; }
    private int searchY() { return segmentY() + SegmentBar.height() + 4; }
    private int listY() { return searchY() + SearchBar.HEIGHT + 6; }
    private int listX() { return panelX + PADDING; }
    private int listH() { return VISIBLE_ROWS * ROW_HEIGHT; }
    private int detailX() { return listX() + LIST_W + SCROLLBAR_W + 2 + COLUMN_GAP; }
    private int footerY() { return panelY + panelH - FOOTER_HEIGHT; }
    private int rowCbX() { return listX() + LIST_W - CHECKBOX_SIZE - 3; }
    private int allCbX() { return panelX + panelW - PADDING - CHECKBOX_SIZE; }
    private int cancelX() { return panelX + panelW - PADDING - ADD_W - 6 - CANCEL_W; }
    private int addX() { return panelX + panelW - PADDING - ADD_W; }
    private int maxScrollRow() { return Math.max(0, lines.size() - VISIBLE_ROWS); }

    private List<String> modeLabels() {
        return List.of(
                Component.translatable("editor.historystages.ingredient_usage.mode.recipes").getString(),
                Component.translatable("editor.historystages.ingredient_usage.mode.items").getString());
    }

    // ========== DATA ==========

    private void rebuildLines() {
        lines.clear();
        String query = searchBar.getText().trim().toLowerCase();
        for (Kind group : List.of(Kind.ONLY_LOCKED, Kind.WORKS_WITHOUT)) {
            List<Entry> matching = new ArrayList<>();
            for (Entry e : entriesByMode.get(mode)) {
                if (e.kind() == group && (query.isEmpty() || e.search().contains(query))) matching.add(e);
            }
            if (matching.isEmpty()) continue;
            lines.add(new Line(group, null));
            for (Entry e : matching) lines.add(new Line(group, e));
        }
        scrollRow = Math.min(scrollRow, maxScrollRow());
    }

    private Set<String> selected() { return selectedByMode.get(mode); }

    /** The selectable rows of a group as currently filtered, or of every group for null. */
    private List<Entry> selectable(Kind group) {
        List<Entry> out = new ArrayList<>();
        for (Line line : lines) {
            if (line.entry() != null && !line.entry().added() && (group == null || line.group() == group)) {
                out.add(line.entry());
            }
        }
        return out;
    }

    private boolean allTicked(List<Entry> entries) {
        if (entries.isEmpty()) return false;
        for (Entry e : entries) if (!selected().contains(e.id())) return false;
        return true;
    }

    private void setAll(List<Entry> entries, boolean on) {
        for (Entry e : entries) {
            if (on) selected().add(e.id()); else selected().remove(e.id());
        }
    }

    private int count(Kind group) {
        int n = 0;
        for (Line line : lines) if (line.entry() != null && line.group() == group) n++;
        return n;
    }

    private Entry shownInDetail() {
        return hovered != null ? hovered : pinned;
    }

    // ========== RENDER ==========

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (!visible) return;

        g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0x88000000);
        g.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF3D3D3D);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF1A1A1A);

        renderHeader(g, font, mouseX, mouseY);

        List<String> labels = modeLabels();
        int segX = panelX + PADDING;
        int over = SegmentBar.segmentAt(font, segX, segmentY(), mouseX, mouseY, labels);
        modeState.update(mode, over, labels.size());
        SegmentBar.draw(g, font, segX, segmentY(), labels, mode, modeState, new boolean[labels.size()]);

        searchBar.render(g, font, mouseX, mouseY);

        renderList(g, font, mouseX, mouseY);
        renderDetail(g, font, mouseX, mouseY);
        renderFooter(g, font, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int total = entriesByMode.get(mode).size();
        String key = mode == MODE_RECIPES
                ? "editor.historystages.ingredient_usage.title"
                : "editor.historystages.ingredient_usage.title_items";
        String title = Component.translatable(key, clickedName, total).getString();
        String allLabel = Component.translatable("editor.historystages.ingredient_usage.all").getString();
        int cbX = allCbX();
        int titleMax = cbX - font.width(allLabel) - 12 - (panelX + PADDING);
        if (font.width(title) > titleMax) title = font.plainSubstrByWidth(title, titleMax - 8) + "...";
        g.drawString(font, title, panelX + PADDING, panelY + 7, 0xFFFFFF, false);
        g.drawString(font, allLabel, cbX - font.width(allLabel) - 5, panelY + 7, 0x999999, false);
        renderCheckbox(g, cbX, panelY + 5, allTicked(selectable(null)), mouseX, mouseY, "all");
    }

    private void renderList(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int x = listX();
        int y = listY();
        hovered = null;

        if (lines.isEmpty()) {
            String key = entriesByMode.get(mode).isEmpty()
                    ? "editor.historystages.ingredient_usage.empty"
                    : "editor.historystages.ingredient_usage.no_match";
            String text = Component.translatable(key).getString();
            g.drawString(font, text, x + (LIST_W - font.width(text)) / 2, y + listH() / 2 - 4,
                    0x777777, false);
            return;
        }

        g.enableScissor(x, y, x + LIST_W, y + listH());
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scrollRow + i;
            if (index >= lines.size()) break;
            Line line = lines.get(index);
            int rowY = y + i * ROW_HEIGHT;
            boolean rowHovered = mouseX >= x && mouseX < x + LIST_W && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            int cbY = rowY + (ROW_HEIGHT - CHECKBOX_SIZE) / 2;

            if (line.entry() == null) {
                int color = line.group() == Kind.ONLY_LOCKED ? GROUP_LOCKED_COLOR : GROUP_ALT_COLOR;
                String key = line.group() == Kind.ONLY_LOCKED
                        ? "editor.historystages.ingredient_usage.group.only_locked"
                        : "editor.historystages.ingredient_usage.group.works_without";
                String text = Component.translatable(key, count(line.group())).getString();
                g.drawString(font, text, x + 4, rowY + 6, color, false);
                g.fill(x, rowY + ROW_HEIGHT - 2, x + LIST_W, rowY + ROW_HEIGHT - 1, (color & 0x00FFFFFF) | 0x80000000);
                renderCheckbox(g, rowCbX(), cbY - 1, allTicked(selectable(line.group())), mouseX, mouseY,
                        "group." + line.group());
                continue;
            }

            Entry e = line.entry();
            if (rowHovered) {
                hovered = e;
                // Hovering pins too, so the card stays put while the cursor moves over to it.
                if (pinned != e) {
                    pinned = e;
                    detailScroll = 0;
                }
            }
            boolean isPinned = pinned != null && pinned.id().equals(e.id());
            g.fill(x, rowY, x + LIST_W, rowY + ROW_HEIGHT - 1,
                    rowHovered && !e.added() ? 0xFF353535 : isPinned ? 0xFF2D2D2D : 0xFF252525);
            if (isPinned) g.fill(x, rowY, x + 2, rowY + ROW_HEIGHT - 1, 0xFFFFCC00);

            g.renderItem(e.stack(), x + 4, rowY + 1);
            String note = e.added()
                    ? Component.translatable("editor.historystages.ingredient_usage.already_added").getString()
                    : e.note();
            int noteW = note.isEmpty() ? 0 : font.width(note) + 6;
            int textX = x + 24;
            int textMax = rowCbX() - 6 - noteW - textX;
            String name = e.stack().getHoverName().getString();
            if (font.width(name) > textMax) name = font.plainSubstrByWidth(name, textMax - 6) + "...";
            int nameColor = e.added() ? 0x555555 : e.kind() == Kind.WORKS_WITHOUT ? 0x999999 : 0xDDDDDD;
            g.drawString(font, name, textX, rowY + 5, nameColor, false);
            if (!note.isEmpty()) {
                g.drawString(font, note, rowCbX() - 6 - font.width(note), rowY + 5, 0x666666, false);
            }
            if (!e.added()) {
                renderCheckbox(g, rowCbX(), cbY, selected().contains(e.id()), mouseX, mouseY, e.id());
            }
        }
        g.disableScissor();

        int max = maxScrollRow();
        if (max > 0) {
            int barX = x + LIST_W + 2;
            g.fill(barX, y, barX + SCROLLBAR_W, y + listH(), 0xFF252525);
            int thumbH = Math.max(10, (int) ((float) VISIBLE_ROWS / lines.size() * listH()));
            int thumbY = y + (int) ((float) scrollRow / max * (listH() - thumbH));
            g.fill(barX, thumbY, barX + SCROLLBAR_W, thumbY + thumbH, 0xFF888888);
        }
    }

    private void renderDetail(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int x = detailX();
        int y = listY();
        int w = detailW();
        g.fill(x - COLUMN_GAP / 2, y, x - COLUMN_GAP / 2 + 1, y + listH(), 0xFF333333);

        Entry e = shownInDetail();
        if (e == null) {
            String hint = Component.translatable("editor.historystages.ingredient_usage.detail_hint").getString();
            List<net.minecraft.util.FormattedCharSequence> wrapped = font.split(Component.literal(hint), w - 8);
            int ty = y + listH() / 2 - wrapped.size() * 5;
            for (var line : wrapped) {
                g.drawString(font, line, x + (w - font.width(line)) / 2, ty, 0x666666, false);
                ty += 10;
            }
            renderLegend(g, font, x, y + listH() - 20);
            return;
        }

        g.renderItem(e.stack(), x, y + 1);
        String name = e.stack().getHoverName().getString();
        if (font.width(name) > w - 22) name = font.plainSubstrByWidth(name, w - 28) + "...";
        g.drawString(font, name, x + 20, y + 5, 0xFFFFFF, false);

        int cardsTop = y + DETAIL_HEADER_H;
        int cardsH = listH() - DETAIL_HEADER_H - 22;
        int cardW = w - 6;
        detailScroll = Math.max(0, Math.min(detailScroll, maxDetailScroll(e, cardsH)));
        g.enableScissor(x, cardsTop, x + w, cardsTop + cardsH);
        int cy = cardsTop - detailScroll;
        for (RecipeRow card : e.cards()) {
            int h = card.shape().layout().cardHeight();
            boolean cardHovered = mouseX >= x && mouseX < x + cardW && mouseY >= cy && mouseY < cy + h
                    && mouseY >= cardsTop && mouseY < cardsTop + cardsH;
            RecipeCardRenderer.render(g, font, card.shape(), card.result(), "", List.of(),
                    card.typeId(), card.recipeId(), x, cy, cardW, cardHovered, false, card.slotMarks());
            cy += h + CARD_GAP;
        }
        g.disableScissor();
        renderLegend(g, font, x, y + listH() - 20);
    }

    private void renderLegend(GuiGraphics g, Font font, int x, int y) {
        int sw = 7;
        g.renderOutline(x, y + 1, sw, sw, RecipeCardRenderer.MARK_LOCKED_COLOR);
        g.drawString(font, Component.translatable("editor.historystages.ingredient_usage.legend.locked").getString(),
                x + sw + 4, y, 0x888888, false);
        g.fill(x, y + 11, x + 2, y + 12, RecipeCardRenderer.MARK_ALTERNATIVE_COLOR);
        g.fill(x + 4, y + 11, x + 6, y + 12, RecipeCardRenderer.MARK_ALTERNATIVE_COLOR);
        g.fill(x, y + 17, x + 2, y + 18, RecipeCardRenderer.MARK_ALTERNATIVE_COLOR);
        g.fill(x + 4, y + 17, x + 6, y + 18, RecipeCardRenderer.MARK_ALTERNATIVE_COLOR);
        g.drawString(font, Component.translatable("editor.historystages.ingredient_usage.legend.alternative").getString(),
                x + sw + 4, y + 10, 0x888888, false);
    }

    private int maxDetailScroll(Entry e, int cardsH) {
        int total = 0;
        for (RecipeRow card : e.cards()) total += card.shape().layout().cardHeight() + CARD_GAP;
        return Math.max(0, total - CARD_GAP - cardsH);
    }

    private void renderFooter(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int fy = footerY() + 5;
        int n = selected().size();
        String note = Component.translatable("editor.historystages.ingredient_usage.selected", n).getString();
        g.drawString(font, note, panelX + PADDING, fy + 6, 0x888888, false);

        boolean overCancel = isIn(mouseX, mouseY, cancelX(), fy, CANCEL_W, BTN_H);
        boolean overAdd = n > 0 && isIn(mouseX, mouseY, addX(), fy, ADD_W, BTN_H);
        renderButton(g, font, Component.translatable("editor.historystages.cancel").getString(),
                cancelX(), fy, CANCEL_W, Ease.outCubic(cancelHover.ramp(overCancel, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS)), true);
        renderButton(g, font, Component.translatable("editor.historystages.ingredient_usage.add", n).getString(),
                addX(), fy, ADD_W, Ease.outCubic(addHover.ramp(overAdd, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS)), n > 0);

        if (isIn(mouseX, mouseY, panelX + PADDING, fy + 4, font.width(note), 12)) {
            Minecraft mc = Minecraft.getInstance();
            EditorTooltip.draw(g, font,
                    Component.translatable("editor.historystages.ingredient_usage.snapshot_tooltip").getString(),
                    mouseX, mouseY, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        } else {
            renderCardTooltip(g, font, mouseX, mouseY);
        }
    }

    private void renderCardTooltip(GuiGraphics g, Font font, int mouseX, int mouseY) {
        Entry e = shownInDetail();
        if (e == null) return;
        int x = detailX();
        int cardsTop = listY() + DETAIL_HEADER_H;
        int cardsH = listH() - DETAIL_HEADER_H - 22;
        if (mouseY < cardsTop || mouseY >= cardsTop + cardsH) return;
        int cy = cardsTop - detailScroll;
        for (RecipeRow card : e.cards()) {
            int h = card.shape().layout().cardHeight();
            if (mouseY >= cy && mouseY < cy + h) {
                ItemStack stack = RecipeCardRenderer.stackAt(card.shape(), card.result(), x, cy,
                        detailW() - 6, mouseX, mouseY);
                String text = stack.isEmpty()
                        ? card.recipeId() + "\n§8" + card.typeId()
                        : stack.getHoverName().getString() + "\n§8" + BuiltInRegistries.ITEM.getKey(stack.getItem());
                int slot = RecipeCardRenderer.inputSlotAt(card.shape(), x, cy, detailW() - 6, mouseX, mouseY);
                if (slot >= 0 && slot < card.alternatives().size() && !card.alternatives().get(slot).isEmpty()) {
                    text += alternativesText(card.alternatives().get(slot));
                }
                Minecraft mc = Minecraft.getInstance();
                EditorTooltip.draw(g, font, text, mouseX, mouseY,
                        mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
                return;
            }
            cy += h + CARD_GAP;
        }
    }

    /** "Also works with:" plus the unlocked items of a dashed slot, capped so the tooltip fits. */
    private static String alternativesText(List<ItemStack> alternatives) {
        StringBuilder text = new StringBuilder("\n\n§6")
                .append(Component.translatable("editor.historystages.ingredient_usage.also_with").getString());
        int shown = Math.min(MAX_ALTERNATIVES, alternatives.size());
        for (int i = 0; i < shown; i++) {
            text.append("\n§7").append(alternatives.get(i).getHoverName().getString());
        }
        if (alternatives.size() > shown) {
            text.append("\n§8").append(Component.translatable(
                    "editor.historystages.ingredient_usage.also_with_more", alternatives.size() - shown).getString());
        }
        return text.toString();
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

    private void renderButton(GuiGraphics g, Font font, String text, int x, int y, int w, float hp, boolean enabled) {
        int bgAlpha = (int) (0x30 + hp * 0x20);
        int bgG = (int) (0xFF - hp * 0x33);
        int bgB = (int) (0xFF - hp * 0xFF);
        g.fill(x, y, x + w, y + BTN_H, (bgAlpha << 24) | (0xFF << 16) | (bgG << 8) | bgB);
        int accentAlpha = enabled ? (int) (0x60 + hp * 0x9F) : 0x30;
        g.fill(x, y + BTN_H - 2, x + w, y + BTN_H, (accentAlpha << 24) | 0xFFCC00);
        g.fill(x, y, x + w, y + 1, 0x20FFFFFF);
        g.fill(x, y, x + 1, y + BTN_H, 0x15FFFFFF);
        g.fill(x + w - 1, y, x + w, y + BTN_H, 0x15FFFFFF);
        int gray = enabled ? (int) (0xCC + hp * 0x33) : 0x66;
        g.drawString(font, text, x + (w - font.width(text)) / 2, y + (BTN_H - 8) / 2,
                (0xFF << 24) | (gray << 16) | (gray << 8) | gray, false);
    }

    private static boolean isIn(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ========== INPUT ==========

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!visible) return false;
        if (!isIn(mouseX, mouseY, panelX, panelY, panelW, panelH)) {
            hide();
            return true;
        }

        if (searchBar.mouseClicked(mouseX, mouseY)) return true;
        searchBar.setFocused(false);

        int picked = SegmentBar.indexAt(Minecraft.getInstance().font, panelX + PADDING, mouseX, modeLabels());
        if (mouseY >= segmentY() && mouseY < segmentY() + SegmentBar.height() && picked >= 0) {
            if (picked != mode) {
                click();
                mode = picked;
                pinned = null;
                scrollRow = 0;
                detailScroll = 0;
                rebuildLines();
            }
            return true;
        }

        if (isIn(mouseX, mouseY, allCbX(), panelY + 5, CHECKBOX_SIZE, CHECKBOX_SIZE)) {
            click();
            List<Entry> all = selectable(null);
            setAll(all, !allTicked(all));
            return true;
        }

        int fy = footerY() + 5;
        if (isIn(mouseX, mouseY, cancelX(), fy, CANCEL_W, BTN_H)) {
            click();
            hide();
            return true;
        }
        if (isIn(mouseX, mouseY, addX(), fy, ADD_W, BTN_H) && !selected().isEmpty()) {
            click();
            confirm();
            return true;
        }

        if (maxScrollRow() > 0 && isIn(mouseX, mouseY, listX() + LIST_W, listY(), SCROLLBAR_W + 4, listH())) {
            draggingScrollbar = true;
            scrollFromMouse(mouseY);
            return true;
        }

        if (isIn(mouseX, mouseY, listX(), listY(), LIST_W, listH())) {
            int index = scrollRow + (int) ((mouseY - listY()) / ROW_HEIGHT);
            if (index < 0 || index >= lines.size()) return true;
            Line line = lines.get(index);
            if (line.entry() == null) {
                click();
                List<Entry> group = selectable(line.group());
                setAll(group, !allTicked(group));
                return true;
            }
            Entry e = line.entry();
            pinned = e;
            detailScroll = 0;
            // The whole row toggles, as in the mod popup; an already-added row only pins.
            if (!e.added()) {
                click();
                if (!selected().remove(e.id())) selected().add(e.id());
            }
            return true;
        }
        return true;
    }

    private void confirm() {
        List<String> ids = new ArrayList<>();
        // In list order rather than click order, so the stage's entry list reads like the popup.
        for (Entry e : entriesByMode.get(mode)) {
            if (selected().contains(e.id()) && !e.added()) ids.add(e.id());
        }
        hide();
        if (mode == MODE_RECIPES) onAddRecipes.accept(ids); else onAddItems.accept(ids);
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (!visible || !draggingScrollbar) return false;
        scrollFromMouse(mouseY);
        return true;
    }

    public boolean mouseReleased() {
        if (draggingScrollbar) { draggingScrollbar = false; return true; }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!visible) return false;
        if (mouseX >= detailX() && shownInDetail() != null) {
            detailScroll = Math.max(0, detailScroll - (int) (scrollY * 12));
        } else {
            scrollRow = Math.max(0, Math.min(maxScrollRow(), scrollRow - (int) scrollY));
        }
        return true;
    }

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

    public boolean charTyped(char c) {
        if (!visible) return false;
        searchBar.charTyped(c);
        return true;
    }

    private void scrollFromMouse(double mouseY) {
        int max = maxScrollRow();
        int thumbH = Math.max(10, (int) ((float) VISIBLE_ROWS / Math.max(1, lines.size()) * listH()));
        float usable = listH() - thumbH;
        if (usable <= 0) return;
        float ratio = (float) (mouseY - listY() - thumbH / 2.0) / usable;
        scrollRow = Math.round(Math.max(0, Math.min(1, ratio)) * max);
    }
}
