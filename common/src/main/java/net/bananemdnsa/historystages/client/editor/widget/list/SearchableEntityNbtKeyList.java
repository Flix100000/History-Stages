package net.bananemdnsa.historystages.client.editor.widget.list;

import net.bananemdnsa.historystages.api.editor.widget.AbstractSearchableList;
import net.bananemdnsa.historystages.client.editor.widget.EditorTooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The picker behind "add criterion" on the entity NBT screen, built like
 * {@link SearchableNbtCriterionList}: common properties by name, every key a fresh entity of the
 * type saves, and a key typed by hand.
 *
 * <p>The first two tabs are not the same list twice. Flags such as {@code Silent} are only saved
 * once switched on, so a fresh entity never shows them; that is what the named presets cover.
 */
public class SearchableEntityNbtKeyList extends AbstractSearchableList<SearchableEntityNbtKeyList.Option> {

    /** Selection for the hand-typed row. Real keys are handed back as they are. */
    public static final String CUSTOM_KEY = "\u0000custom";

    /**
     * @param key     the NBT key, or {@link #CUSTOM_KEY}
     * @param label   what the row shows
     * @param detail  the default value, dimmed in the second column
     * @param tooltip the full text on hover
     */
    public record Option(String key, String label, String detail, String tooltip) {}

    private static final int TAB_COMMON = 0;
    private static final int TAB_KEYS = 1;
    private static final int TAB_CUSTOM = 2;
    private static final float DETAIL_COLUMN = 0.42f;

    private final List<Option> commonOptions;
    private final List<Option> keyOptions;
    private int activeTab = TAB_COMMON;
    private final EditorTooltip tooltip = new EditorTooltip();
    private Option hoveredEntry;

    public SearchableEntityNbtKeyList(List<Option> commonOptions, List<Option> keyOptions,
                                      Consumer<String> onSelect,
                                      Supplier<Collection<String>> alreadyAddedSupplier) {
        super(Component.translatable("editor.historystages.nbt.picker.placeholder").getString(),
                onSelect, alreadyAddedSupplier);
        this.commonOptions = commonOptions;
        this.keyOptions = keyOptions;
        reloadEntries();
    }

    @Override
    protected List<String> ownTabLabels() {
        return List.of(
                Component.translatable("editor.historystages.nbt.tab.common").getString(),
                Component.translatable("editor.historystages.entity_nbt.tab.keys").getString(),
                Component.translatable("editor.historystages.entity_nbt.tab.custom").getString());
    }

    @Override
    protected void onOwnTabChanged(int index) {
        activeTab = index;
        reloadEntries();
    }

    @Override
    protected int getPanelWidth() {
        return 280;
    }

    @Override
    protected List<Option> loadEntries() {
        // Called once from the superclass constructor, before keyOptions is set.
        if (keyOptions == null) return List.of();
        if (activeTab == TAB_COMMON) return commonOptions;
        if (activeTab == TAB_CUSTOM) {
            return List.of(new Option(CUSTOM_KEY,
                    Component.translatable("editor.historystages.nbt.picker.custom_key").getString(), "",
                    Component.translatable("editor.historystages.entity_nbt.desc").getString()));
        }
        return keyOptions;
    }

    @Override
    protected String getIdForFilter(Option entry) {
        return entry.key();
    }

    @Override
    protected String getIdForAddedCheck(Option entry) {
        return entry.key();
    }

    @Override
    protected boolean matchesQuery(Option entry, String lowerCaseQuery) {
        return entry.label().toLowerCase().contains(lowerCaseQuery)
                || entry.detail().toLowerCase().contains(lowerCaseQuery)
                || entry.key().toLowerCase().contains(lowerCaseQuery);
    }

    @Override
    protected String selectionValueOf(Option entry) {
        return entry.key();
    }

    @Override
    protected void renderRow(GuiGraphicsExtractor g, Font font, Option entry,
                             int x, int y, int w, int h, boolean hovered, int rowIndex) {
        int labelX = x + 3;
        int detailX = x + 3 + Math.round((w - 9) * DETAIL_COLUMN);

        String label = fit(font, entry.label(), detailX - 6 - labelX);
        g.text(font, label, labelX, y + 4, hovered ? 0xFFFFFFFF : 0xFFBBBBBB, false);

        if (!entry.detail().isEmpty()) {
            String detail = fit(font, entry.detail(), x + w - 6 - detailX);
            g.text(font, detail, detailX, y + 4, 0xFF777777, false);
        }

        if (hovered) hoveredEntry = entry;
    }

    @Override
    protected void afterRender(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        Option entry = hoveredEntry;
        hoveredEntry = null;

        var window = Minecraft.getInstance().getWindow();
        tooltip.render(g, font,
                entry == null ? null : entry.label(),
                entry == null ? null : entry.tooltip(),
                mouseX, mouseY, window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }

    private static String fit(Font font, String text, int room) {
        if (room <= 0) return "";
        if (font.width(text) <= room) return text;
        int ellipsis = font.width("...");
        if (room <= ellipsis) return "";
        return font.plainSubstrByWidth(text, room - ellipsis) + "...";
    }
}
