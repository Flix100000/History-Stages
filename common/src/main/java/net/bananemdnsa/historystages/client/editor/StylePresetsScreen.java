package net.bananemdnsa.historystages.client.editor;

import net.bananemdnsa.historystages.api.editor.TabInputContext;
import net.bananemdnsa.historystages.api.editor.TabRenderContext;
import net.bananemdnsa.historystages.api.editor.widget.EditorRowList;
import net.bananemdnsa.historystages.client.editor.dialog.StylePresetNameScreen;
import net.bananemdnsa.historystages.client.editor.graph.PresetSwatch;
import net.bananemdnsa.historystages.client.editor.graph.StageGraphConfig;
import net.bananemdnsa.historystages.client.editor.widget.ConfirmDialog;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.bananemdnsa.historystages.data.graph.GraphStageData;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.serverbound.DeleteStylePresetPacket;
import net.bananemdnsa.historystages.network.serverbound.SaveStylePresetPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

/**
 * Every style preset of the pack, with how many stages use it, and the ways to edit, rename,
 * delete and create one. Opened from the stage graph's top bar. Built like
 * {@link DisguiseListScreen}: a plain screen with the editor's card rows.
 */
public class StylePresetsScreen extends Screen {

    private static final int MARGIN = 10;
    private static final int LIST_TOP = 48;
    private static final int GREY_TEXT = 0xFF777777;
    private static final int USAGE_COLOR = 0x999999;

    private final Screen parent;
    private final EditorRowList rows = new EditorRowList();
    private double scroll;

    /** Read each frame, so a save, rename or delete shows at once. */
    private List<Map.Entry<String, GraphStageData.Preset>> presets = List.of();

    public StylePresetsScreen(Screen parent) {
        super(Component.translatable("editor.historystages.graph.preset.manage.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(StyledButton.of(Component.translatable("editor.historystages.graph.preset.new"),
                b -> this.minecraft.setScreen(new StylePresetNameScreen(this,
                        Component.translatable("editor.historystages.graph.preset.new.title"),
                        null, null, this::create)),
                this.width - MARGIN - 110, 22, 110, 20));
        addRenderableWidget(StyledButton.of(Component.translatable("editor.historystages.back"),
                b -> onClose(), MARGIN, this.height - 28, 60, 20));
    }

    // --- rendering ---

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Drawn in render(); leaving this empty keeps 1.21's blur out of the editor.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xE0101010);
        g.drawCenteredString(this.font, this.title, this.width / 2, 6, 0xFFFFFF);
        g.fill(MARGIN, 19, this.width - MARGIN, 20, 0x40FFFFFF);

        presets = GraphStageData.get().presetsByName();
        usageColumnW = 0;
        for (Map.Entry<String, GraphStageData.Preset> entry : presets) {
            usageColumnW = Math.max(usageColumnW, this.font.width(usageLabel(entry.getKey())) + 6);
        }
        int listBottom = listBottom();
        int maxScroll = Math.max(0, EditorRowList.heightFor(presets.size()) - (listBottom - LIST_TOP));
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        if (presets.isEmpty()) {
            int y = LIST_TOP + 20;
            for (var line : this.font.split(Component.translatable("editor.historystages.graph.preset.manage.empty"),
                    Math.min(360, this.width - MARGIN * 4))) {
                g.drawCenteredString(this.font, line, this.width / 2, y, GREY_TEXT);
                y += 11;
            }
        } else {
            g.enableScissor(0, LIST_TOP, this.width, listBottom);
            rows.render(new TabRenderContext(g, this.font, MARGIN, LIST_TOP - (int) scroll,
                            this.width - MARGIN * 2, LIST_TOP, listBottom,
                            mouseX, mouseY, false, (a, b) -> {}),
                    presets.size(), (row, i) -> describe(row, presets.get(i)));
            g.disableScissor();
        }
        g.fill(MARGIN, listBottom + 1, this.width - MARGIN, listBottom + 2, 0xFF555555);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void describe(EditorRowList.Row row, Map.Entry<String, GraphStageData.Preset> entry) {
        String id = entry.getKey();
        GraphStageData.Preset preset = entry.getValue();
        row.leading(16, (g, x, y, w, h) -> PresetSwatch.draw(g, id, x + 7, y + h / 2, 7));
        row.text(preset.name);

        // Slots stack right to left in the order they are declared. One width for all three, so
        // they stand in columns down the list.
        String deleteLabel = Component.translatable("editor.historystages.graph.preset.action.delete").getString();
        String renameLabel = Component.translatable("editor.historystages.graph.preset.action.rename").getString();
        String editLabel = Component.translatable("editor.historystages.graph.preset.action.edit").getString();
        int buttonW = Math.max(font.width(deleteLabel), Math.max(font.width(renameLabel), font.width(editLabel))) + 12;
        row.button(deleteLabel, null, buttonW, () -> confirmDelete(id, preset));
        row.button(renameLabel, null, buttonW, () -> this.minecraft.setScreen(new StylePresetNameScreen(this,
                Component.translatable("editor.historystages.graph.preset.rename.title"),
                id, preset.name, name -> rename(id, preset, name))));
        row.button(editLabel, null, buttonW, () -> this.minecraft.setScreen(StageStyleScreen.forPreset(this, id)));

        row.badge(usageLabel(id), USAGE_COLOR, usageColumnW);
    }

    private String usageLabel(String id) {
        int uses = GraphStageData.get().usageCount(id);
        return uses == 0
                ? Component.translatable("editor.historystages.graph.preset.unused").getString()
                : Component.translatable(uses == 1 ? "editor.historystages.graph.preset.used.one"
                        : "editor.historystages.graph.preset.used.many", uses).getString();
    }

    /** Wide enough for the longest usage text this frame, so the counts form a column too. */
    private int usageColumnW;

    private int listBottom() {
        return this.height - 36;
    }

    // --- actions ---

    private void create(String name) {
        GraphStageData.Snapshot data = GraphStageData.get();
        // The server derives the id from the same name and list, so this is the id it stores.
        String id = GraphStageData.newPresetId(name, data.presets().keySet());
        GraphStageData.Preset preset = new GraphStageData.Preset();
        preset.name = name;
        PacketHandler.sendToServer(new SaveStylePresetPacket(id, name, "{}"));
        GraphStageData.set(data.withPreset(id, preset));
        this.minecraft.setScreen(StageStyleScreen.forPreset(this, id));
    }

    private void rename(String id, GraphStageData.Preset preset, String name) {
        GraphStageData.Preset renamed = preset.copy();
        renamed.name = name;
        GraphStageData.Entry blocks = new GraphStageData.Entry();
        blocks.style = renamed.style;
        blocks.styles = renamed.styles;
        PacketHandler.sendToServer(new SaveStylePresetPacket(id, name, GraphStageData.entryToJson(blocks)));
        GraphStageData.set(GraphStageData.get().withPreset(id, renamed));
    }

    private void confirmDelete(String id, GraphStageData.Preset preset) {
        int uses = GraphStageData.get().usageCount(id);
        Component message = uses == 0
                ? Component.translatable("editor.historystages.graph.preset.delete.confirm_unused", preset.name)
                : Component.translatable("editor.historystages.graph.preset.delete.confirm_used", preset.name, uses);
        this.minecraft.setScreen(new ConfirmDialog(this,
                Component.translatable("editor.historystages.graph.preset.delete.title"),
                message,
                () -> {
                    PacketHandler.sendToServer(new DeleteStylePresetPacket(id));
                    GraphStageData.set(GraphStageData.get().withoutPreset(id));
                    StageGraphConfig.invalidateCache();
                    this.minecraft.setScreen(this);
                }));
    }

    // --- input ---

    private TabInputContext input(double mouseX, double mouseY) {
        return new TabInputContext(MARGIN, LIST_TOP - (int) scroll, this.width - MARGIN * 2,
                LIST_TOP, listBottom(), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        TabInputContext ctx = input(mouseX, mouseY);
        if (rows.mouseClicked(ctx)) return true;
        // A click on the row itself, beside the buttons, edits — the most common thing to do.
        int index = rows.rowAt(ctx, presets.size());
        if (index < 0) return false;
        this.minecraft.setScreen(StageStyleScreen.forPreset(this, presets.get(index).getKey()));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= scrollY * (EditorRowList.CARD_HEIGHT + EditorRowList.CARD_GAP);
        return true;
    }

    @Override
    public void onClose() {
        StageGraphConfig.invalidateCache();
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
