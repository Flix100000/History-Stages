package net.bananemdnsa.historystages.client.editor.widget.popup.spawn;

import net.bananemdnsa.historystages.api.editor.widget.NumberStepper;
import net.bananemdnsa.historystages.api.editor.widget.PickerOverlay;
import net.bananemdnsa.historystages.client.editor.widget.EditorTooltip;
import net.bananemdnsa.historystages.client.editor.widget.dropdown.EnumDropdown;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableBiomeList;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableDimensionList;
import net.bananemdnsa.historystages.data.lock.EntitySpawnLockEntry;
import net.bananemdnsa.historystages.data.lock.GenerationPhase;
import net.bananemdnsa.historystages.data.lock.spawn.FilterMode;
import net.bananemdnsa.historystages.data.lock.spawn.SkyCondition;
import net.bananemdnsa.historystages.data.lock.spawn.TimeOfDay;
import net.bananemdnsa.historystages.data.lock.spawn.WeatherCondition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static net.bananemdnsa.historystages.client.editor.widget.popup.spawn.SpawnPageRows.K;
import static net.bananemdnsa.historystages.client.editor.widget.popup.spawn.SpawnPageRows.addOnce;
import static net.bananemdnsa.historystages.client.editor.widget.popup.spawn.SpawnPageRows.countBadge;

/**
 * One spawn-lock entry's whole rule: phase on top, four tabs on the left (sources, location, time &
 * weather, extra biomes), and a sentence underneath that says in plain words what the rule does.
 *
 * <p>The sentence always describes the complete rule, whichever tab is open — a condition set on a
 * tab nobody is looking at is the typical way to confuse yourself here.
 *
 * <p>Each tab is a method that calls {@link SpawnPageRows} helpers in order, plus the row state
 * that belongs to it. What a tab <em>means</em> lives in {@link SpawnRuleDraft} and
 * {@link SpawnRuleSummary} instead, which is what lets those two be unit-tested without Minecraft.
 *
 * <p>On confirm it reports the entity id and the full entry, a plain lock included.
 */
public class SpawnControlPopup {

    private static final int WIDTH = 390;
    private static final int PAD = 10;
    private static final int HEADER_H = 22;
    private static final int PHASE_ROW_H = 24;
    private static final int TAB_W = 104;
    private static final int TAB_H = 18;
    private static final int BODY_H = 184;
    /** Four tabs plus a little air. Below this the tab column itself would be cut off. */
    private static final int MIN_BODY_H = 92;
    private static final int SUMMARY_LINES = 3;
    private static final int FOOTER_H = 26;
    private static final int BTN_H = 16;

    private static final List<String> FILTER = List.of(K + "any", K + "only", K + "exclude");
    private static final List<String> RANGE = List.of(K + "any", K + "range");
    private static final List<String> SKY = List.of(K + "any", K + "sky.visible", K + "sky.hidden");
    private static final List<String> TIME = List.of(K + "any", K + "time.day", K + "time.night");
    private static final List<String> WEATHER =
            List.of(K + "any", K + "weather.clear", K + "weather.rain", K + "weather.thunder");
    private static final List<String> MOON = List.of(K + "any", K + "moon.pick");
    private static final List<String> FREQUENCY = List.of(K + "extra.default", K + "extra.custom");

    /** One tab: its label, the rows it owns, the count beside the label, and what it draws. */
    private record Tab(String key, SpawnPageRows rows, Supplier<String> badge, Runnable body) {}

    private final BiConsumer<String, EntitySpawnLockEntry> onConfirm;
    private final EnumDropdown phaseDropdown;

    /** What the dialog is editing. Replaced wholesale on show and on reset, never edited around. */
    private SpawnRuleDraft draft = SpawnRuleDraft.from(null);

    private boolean visible;
    private String entityId;
    private int activeTab;
    private PickerOverlay picker;

    private int centerX, centerY;
    private int panelX, panelY, panelW, panelH, bodyH;

    private final SpawnPageRows sourceRows = new SpawnPageRows();
    private final SpawnPageRows locationRows = new SpawnPageRows();
    private final SpawnPageRows timeRows = new SpawnPageRows();
    private final SpawnPageRows extraRows = new SpawnPageRows();

    // Wide enough for modded dimensions with a taller build range.
    private final NumberStepper heightMin = locationRows.stepper(-2032, 2031, v -> draft.heightMin = v);
    private final NumberStepper heightMax = locationRows.stepper(-2032, 2031, v -> draft.heightMax = v);
    private final NumberStepper lightMin = timeRows.stepper(0, 15, v -> draft.lightMin = v);
    private final NumberStepper lightMax = timeRows.stepper(0, 15, v -> draft.lightMax = v);
    private final NumberStepper weight = extraRows.stepper(1, 1000, v -> draft.weight = v);
    private final NumberStepper minGroup = extraRows.stepper(1, 32, v -> draft.minGroup = v);
    private final NumberStepper maxGroup = extraRows.stepper(1, 32, v -> draft.maxGroup = v);

    private final List<Tab> tabs = List.of(
            new Tab(K + "tab.sources", sourceRows,
                    () -> draft.lockedSources.size() + "/" + EntitySpawnLockEntry.ALL_SOURCES.size(),
                    this::drawSources),
            new Tab(K + "tab.location", locationRows,
                    () -> countBadge(draft.locationCount()), this::drawLocation),
            new Tab(K + "tab.time", timeRows,
                    () -> countBadge(draft.timeCount()), this::drawTimeAndWeather),
            new Tab(K + "tab.extra", extraRows,
                    () -> countBadge(draft.extraIds.size()), this::drawExtraBiomes));

    public SpawnControlPopup(BiConsumer<String, EntitySpawnLockEntry> onConfirm) {
        this.onConfirm = onConfirm;
        this.phaseDropdown = new EnumDropdown(
                List.of(GenerationPhase.WHILE_LOCKED.serialize(), GenerationPhase.AFTER_UNLOCK.serialize()),
                GenerationPhase.WHILE_LOCKED.serialize(), 0,
                raw -> Component.translatable("editor.historystages.generation.mode."
                        + GenerationPhase.parse(raw).serialize()),
                raw -> draft.setPhase(GenerationPhase.parse(raw)));
    }

    public boolean isVisible() { return visible; }

    public void hide() { visible = false; }

    /** @param current the stored entry, or null for a plain lock */
    public void show(String entityId, EntitySpawnLockEntry current, int centerX, int centerY) {
        this.entityId = entityId;
        this.centerX = centerX;
        this.centerY = centerY;
        load(SpawnRuleDraft.from(current));
        this.activeTab = 0;
        this.picker = null;
        this.visible = true;
    }

    private void load(SpawnRuleDraft next) {
        draft = next;
        heightMin.setValue(next.heightMin);
        heightMax.setValue(next.heightMax);
        lightMin.setValue(next.lightMin);
        lightMax.setValue(next.lightMax);
        weight.setValue(next.weight);
        minGroup.setValue(next.minGroup);
        maxGroup.setValue(next.maxGroup);
        for (Tab tab : tabs) tab.rows().resetScroll();
        phaseDropdown.setValue(next.phase().serialize());
        phaseDropdown.close();
    }

    // ---- the four tabs -------------------------------------------------------------

    private void drawSources() {
        String hovered = null;
        for (String source : EntitySpawnLockEntry.ALL_SOURCES) {
            if (sourceRows.rowHovered()) hovered = source;
            sourceRows.checkboxRow("editor.historystages.spawn_sources.source." + source,
                    draft.lockedSources.contains(source), true, () -> draft.toggleSource(source));
        }
        sourceRows.hint(hovered != null
                ? "editor.historystages.spawn_sources.desc." + hovered
                : K + "sources.hint");
    }

    private void drawLocation() {
        locationRows.segmentRow(K + "dimensions", FILTER, modeIndex(draft.dimensionMode),
                i -> draft.dimensionMode = modeAt(i));
        if (draft.dimensionMode != null) {
            locationRows.chipRow(draft.dimensionIds, () -> openPicker(new SearchableDimensionList(id -> {
                addOnce(draft.dimensionIds, id);
                closePicker();
            }, () -> draft.dimensionIds)));
        }

        locationRows.segmentRow(K + "biomes", FILTER, modeIndex(draft.biomeMode),
                i -> draft.biomeMode = modeAt(i));
        if (draft.biomeMode != null) {
            locationRows.chipRow(draft.biomeIds, () -> openPicker(new SearchableBiomeList(id -> {
                addOnce(draft.biomeIds, id);
                closePicker();
            }, () -> draft.biomeIds, true)));
        }

        locationRows.segmentRow(K + "sky", SKY, draft.sky == null ? 0 : draft.sky.ordinal() + 1,
                i -> draft.sky = i == 0 ? null : SkyCondition.values()[i - 1]);

        locationRows.segmentRow(K + "height", RANGE, draft.heightOn ? 1 : 0, i -> draft.heightOn = i == 1);
        if (draft.heightOn) locationRows.rangeRow(null, heightMin, heightMax);
    }

    private void drawTimeAndWeather() {
        timeRows.segmentRow(K + "time", TIME, draft.time == null ? 0 : draft.time.ordinal() + 1,
                i -> draft.time = i == 0 ? null : TimeOfDay.values()[i - 1]);

        timeRows.segmentRow(K + "light", RANGE, draft.lightOn ? 1 : 0, i -> draft.lightOn = i == 1);
        if (draft.lightOn) timeRows.rangeRow(null, lightMin, lightMax);

        timeRows.segmentRow(K + "weather", WEATHER, draft.weather == null ? 0 : draft.weather.ordinal() + 1,
                i -> draft.weather = i == 0 ? null : WeatherCondition.values()[i - 1]);

        timeRows.segmentRow(K + "moon", MOON, draft.moonOn ? 1 : 0, i -> draft.moonOn = i == 1);
        if (draft.moonOn) timeRows.moonGrid(draft.moonPhases);
    }

    private void drawExtraBiomes() {
        extraRows.labelRow(K + "extra.biomes", !draft.extraIds.isEmpty());
        extraRows.chipRow(draft.extraIds, () -> openPicker(new SearchableBiomeList(id -> {
            addOnce(draft.extraIds, id);
            closePicker();
        }, () -> draft.extraIds, true)));

        // "Default" sits at index 0 like "Any" elsewhere, which also greys the label out.
        extraRows.segmentRow(K + "extra.frequency", FREQUENCY, draft.customWeight ? 1 : 0,
                i -> draft.customWeight = i == 1);
        if (draft.customWeight) {
            extraRows.valueRow(K + "extra.weight", weight);
            extraRows.rangeRow(K + "extra.group", minGroup, maxGroup);
        }

        extraRows.checkboxRow(K + "extra.ignore_rules", draft.ignoreSpawnRules, !draft.extraIds.isEmpty(),
                () -> draft.ignoreSpawnRules = !draft.ignoreSpawnRules);
        extraRows.hint(draft.ignoreSpawnRules ? K + "extra.hint.rules_off" : K + "extra.hint.rules_on");
        extraRows.hint(K + "extra.hint.conditions");
    }

    private static int modeIndex(FilterMode mode) {
        return mode == null ? 0 : mode.ordinal() + 1;
    }

    private static FilterMode modeAt(int index) {
        return index == 0 ? null : FilterMode.values()[index - 1];
    }

    private void openPicker(PickerOverlay overlay) {
        picker = overlay;
        overlay.setFilter("");
        overlay.show(centerX, centerY, Minecraft.getInstance().getWindow().getGuiScaledWidth());
    }

    private void closePicker() {
        if (picker != null) picker.hide();
        picker = null;
    }

    // ---- the dialog ----------------------------------------------------------------

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (!visible) return;

        // Fit the window rather than run off it: the summary gives up lines first, then the page
        // body, which scrolls once it is shorter than what the tab wants to draw.
        int summaryLines = SUMMARY_LINES;
        int available = g.guiHeight() - 8;
        while (summaryLines > 1 && available - chrome(font, summaryLines) < MIN_BODY_H) summaryLines--;
        int summaryH = summaryHeight(font, summaryLines);
        bodyH = Math.max(MIN_BODY_H, Math.min(BODY_H, available - chrome(font, summaryLines)));

        panelW = Math.min(WIDTH, g.guiWidth() - 8);
        panelH = chrome(font, summaryLines) + bodyH;
        panelX = clamp(centerX - panelW / 2, g.guiWidth() - panelW - 4);
        panelY = clamp(centerY - panelH / 2, g.guiHeight() - panelH - 4);

        g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0x88000000);
        g.fill(panelX + 3, panelY + 3, panelX + panelW + 3, panelY + panelH + 3, 0x50000000);
        g.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFF333333);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF1A1A1A);

        g.drawCenteredString(font, Component.translatable(K + "title", entityId),
                panelX + panelW / 2, panelY + 6, 0xFFFFFFFF);
        g.fill(panelX + panelW / 2 - 20, panelY + 17, panelX + panelW / 2 + 20, panelY + 18, 0xFFFFCC00);

        int phaseY = panelY + HEADER_H;
        g.drawString(font, Component.translatable(K + "phase"),
                panelX + PAD, phaseY + (PHASE_ROW_H - font.lineHeight) / 2 + 1, 0xFFCCCCCC, false);
        phaseDropdown.setPosition(panelX + panelW - PAD - phaseDropdown.getWidth(),
                phaseY + (PHASE_ROW_H - EnumDropdown.BUTTON_HEIGHT) / 2);
        phaseDropdown.renderButton(g, font, mouseX, mouseY);

        int bodyY = phaseY + PHASE_ROW_H;
        g.fill(panelX, bodyY - 1, panelX + panelW, bodyY, 0xFF555555);
        renderTabs(g, font, mouseX, mouseY, bodyY);
        g.fill(panelX + TAB_W, bodyY, panelX + TAB_W + 1, bodyY + bodyH, 0xFF333333);

        Tab tab = tabs.get(activeTab);
        g.enableScissor(panelX + TAB_W + 1, bodyY, panelX + panelW, bodyY + bodyH);
        tab.rows().render(new SpawnPageRows.Frame(g, font, mouseX, mouseY, panelX + TAB_W + 1, bodyY,
                panelW - TAB_W - 1, bodyH), tab.body());
        g.disableScissor();

        renderSummary(g, font, bodyY + bodyH, summaryH);
        renderFooter(g, font, mouseX, mouseY);

        // Last, so open lists cover the rows below them instead of being covered.
        phaseDropdown.renderPopup(g, font, mouseX, mouseY);
        if (picker != null && picker.isVisible()) {
            picker.render(g, font, mouseX, mouseY);
            return;
        }
        renderTooltip(g, font, mouseX, mouseY, phaseY);
    }

    /**
     * The explanation for whatever the cursor is resting on. Drawn with no delay: a dialog this
     * dense is being read on purpose, and a row hidden behind a timer helps nobody.
     */
    private void renderTooltip(GuiGraphics g, Font font, int mouseX, int mouseY, int phaseY) {
        if (phaseDropdown.isShowing()) return;
        String tip = inBox(mouseX, mouseY, panelX, phaseY, panelW, PHASE_ROW_H)
                ? Component.translatable(K + "tip.phase").getString()
                : tabs.get(activeTab).rows().tooltip();
        if (tip != null && !tip.isEmpty()) {
            EditorTooltip.draw(g, font, tip, mouseX, mouseY, g.guiWidth(), g.guiHeight());
        }
    }

    private static int chrome(Font font, int summaryLines) {
        return HEADER_H + PHASE_ROW_H + summaryHeight(font, summaryLines) + FOOTER_H;
    }

    private static int summaryHeight(Font font, int lines) {
        return lines * (font.lineHeight + 1) + 8;
    }

    private static int clamp(int value, int max) {
        return Math.max(4, Math.min(value, Math.max(4, max)));
    }

    private void renderTabs(GuiGraphics g, Font font, int mouseX, int mouseY, int bodyY) {
        for (int i = 0; i < tabs.size(); i++) {
            int ty = tabY(bodyY, i);
            boolean on = i == activeTab;
            if (on) {
                g.fill(panelX, ty, panelX + TAB_W, ty + TAB_H, 0xFF242424);
                g.fill(panelX, ty, panelX + 2, ty + TAB_H, 0xFFFFCC00);
            } else if (inBox(mouseX, mouseY, panelX, ty, TAB_W, TAB_H)) {
                g.fill(panelX, ty, panelX + TAB_W, ty + TAB_H, 0xFF202020);
            }
            g.drawString(font, Component.translatable(tabs.get(i).key()), panelX + 8, ty + 5,
                    on ? 0xFFFFFFFF : 0xFF888888, false);
            String badge = tabs.get(i).badge().get();
            if (badge != null) {
                g.drawString(font, badge, panelX + TAB_W - 6 - font.width(badge), ty + 5, 0xFFFFCC00, false);
            }
        }
    }

    private void renderSummary(GuiGraphics g, Font font, int y, int height) {
        g.fill(panelX, y, panelX + panelW, y + height, 0xFF151515);
        g.fill(panelX, y, panelX + panelW, y + 1, 0xFF333333);
        List<FormattedCharSequence> lines =
                font.split(sentence(SpawnRuleSummary.describe(draft.toEntry(entityId))), panelW - 2 * PAD);
        int fits = Math.max(1, (height - 8) / (font.lineHeight + 1));
        int ly = y + 5;
        for (int i = 0; i < Math.min(fits, lines.size()); i++) {
            g.drawString(font, lines.get(i), panelX + PAD, ly, 0xFFB0B0B0, false);
            ly += font.lineHeight + 1;
        }
    }

    // ---- the sentence --------------------------------------------------------------

    private static Component sentence(SpawnRuleSummary.Summary s) {
        MutableComponent out = Component.empty();
        out.append(fragment(s.phase())).append(" ").append(fragment(s.verb()));
        for (int i = 0; i < s.conditions().size(); i++) {
            out.append(i == 0 ? Component.literal(" ")
                    : Component.translatable(SpawnRuleSummary.PREFIX + "separator"));
            out.append(fragment(s.conditions().get(i)));
        }
        if (!s.conditions().isEmpty()) out.append(Component.translatable(SpawnRuleSummary.PREFIX + "end"));
        if (s.sources() != null) out.append(fragment(s.sources()));
        if (s.extra() != null) out.append(fragment(s.extra()));
        return out;
    }

    private static Component fragment(SpawnRuleSummary.Fragment f) {
        return Component.translatable(f.key(), f.args().stream().map(SpawnControlPopup::arg).toArray());
    }

    private static Object arg(Object arg) {
        if (!(arg instanceof SpawnRuleSummary.Names names)) return arg;
        MutableComponent joined = Component.empty();
        for (int i = 0; i < names.ids().size(); i++) {
            if (i > 0) joined.append(Component.translatable(SpawnRuleSummary.PREFIX + "separator"));
            String id = names.ids().get(i);
            joined.append(names.langPrefix() != null
                    ? Component.translatable(names.langPrefix() + id)
                    : Component.literal(id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id));
        }
        return joined;
    }

    // ---- footer and input ----------------------------------------------------------

    private void renderFooter(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int btnY = panelY + panelH - BTN_H - 6;
        drawButton(g, font, mouseX, mouseY, resetX(), btnY, resetW(font), K + "reset", false);
        drawButton(g, font, mouseX, mouseY, doneX(), btnY, 52, "editor.historystages.lock_actions.btn_done", true);
    }

    private void drawButton(GuiGraphics g, Font font, int mouseX, int mouseY, int x, int y, int w, String key,
                            boolean primary) {
        boolean hov = inBox(mouseX, mouseY, x, y, w, BTN_H);
        int face = primary ? (hov ? 0x50FFCC00 : 0x25FFCC00) : (hov ? 0xFF303030 : 0xFF262626);
        int edge = primary ? (hov ? 0xFFFFCC00 : 0x80FFCC00) : (hov ? 0xFF888888 : 0xFF555555);
        g.fill(x, y, x + w, y + BTN_H, face);
        g.fill(x, y + BTN_H - 1, x + w, y + BTN_H, edge);
        g.drawCenteredString(font, Component.translatable(key), x + w / 2, y + 4, hov ? 0xFFFFFF : 0xDDDDDD);
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!visible) return false;
        // Geometry is only known once render() has run; swallow the click until then.
        if (panelW == 0) return true;

        if (picker != null && picker.isVisible()) {
            picker.mouseClicked(mouseX, mouseY);
            return true;
        }

        // Read up front: the dropdown collapses on a click that misses it, and that click should
        // only close the list, not reach the rows underneath.
        boolean listWasOpen = phaseDropdown.isExpanded();
        if (phaseDropdown.mouseClicked(mouseX, mouseY)) return true;
        if (listWasOpen) return true;

        Tab tab = tabs.get(activeTab);
        if (tab.rows().mouseClicked(mouseX, mouseY)) return true;

        int bodyY = panelY + HEADER_H + PHASE_ROW_H;
        for (int i = 0; i < tabs.size(); i++) {
            if (inBox(mouseX, mouseY, panelX, tabY(bodyY, i), TAB_W, TAB_H)) {
                if (i != activeTab) {
                    tab.rows().commitEdits();
                    activeTab = i;
                    SpawnPageRows.playClick();
                }
                return true;
            }
        }

        int btnY = panelY + panelH - BTN_H - 6;
        Font font = Minecraft.getInstance().font;
        if (inBox(mouseX, mouseY, doneX(), btnY, 52, BTN_H)) {
            SpawnPageRows.playClick();
            confirm();
            return true;
        }
        if (inBox(mouseX, mouseY, resetX(), btnY, resetW(font), BTN_H)) {
            SpawnPageRows.playClick();
            load(SpawnRuleDraft.from(null));
            return true;
        }

        if (!inBox(mouseX, mouseY, panelX, panelY, panelW, panelH)) visible = false;
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!visible) return false;
        if (picker != null && picker.isVisible()) {
            picker.mouseScrolled(mouseX, mouseY, scrollY);
        } else {
            tabs.get(activeTab).rows().mouseScrolled(mouseX, mouseY, scrollY);
        }
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        return visible && picker != null && picker.isVisible() && picker.mouseDragged(mouseX, mouseY);
    }

    public boolean mouseReleased() {
        return visible && picker != null && picker.isVisible() && picker.mouseReleased();
    }

    public boolean keyPressed(int keyCode) {
        if (!visible) return false;
        if (picker != null && picker.isVisible()) {
            if (keyCode == 256) {
                closePicker();
                return true;
            }
            return picker.keyPressed(keyCode);
        }
        if (tabs.get(activeTab).rows().keyPressed(keyCode)) return true;
        if (keyCode == 256) {
            // One level at a time: an open list closes first.
            if (phaseDropdown.isExpanded()) {
                phaseDropdown.close();
            } else {
                visible = false;
            }
            return true;
        }
        return false;
    }

    public boolean charTyped(char c) {
        if (!visible) return false;
        if (picker != null && picker.isVisible()) return picker.charTyped(c);
        return tabs.get(activeTab).rows().charTyped(c);
    }

    private void confirm() {
        for (Tab tab : tabs) tab.rows().commitEdits();
        onConfirm.accept(entityId, draft.toEntry(entityId));
        visible = false;
    }

    private int tabY(int bodyY, int index) {
        return bodyY + 4 + index * TAB_H;
    }

    private int doneX() {
        return panelX + panelW - 52 - PAD;
    }

    private int resetX() {
        return panelX + PAD;
    }

    private static int resetW(Font font) {
        return font.width(Component.translatable(K + "reset")) + 16;
    }

    private static boolean inBox(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
