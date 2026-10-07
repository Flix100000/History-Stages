package net.bananemdnsa.historystages.client.editor.dialog;

import net.bananemdnsa.historystages.api.editor.widget.AbstractModalScreen;
import net.bananemdnsa.historystages.client.editor.graph.PresetSwatch;
import net.bananemdnsa.historystages.data.graph.GraphStageData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Picks the style preset for one or more stages: "none" plus every preset, each with a small
 * swatch. A click picks and closes; the single button only cancels. With no presets yet, a
 * second button leads to where they are made.
 */
public class StylePresetPickerScreen extends AbstractModalScreen {

    private static final int ROW_H = 18;
    private static final int MAX_VISIBLE = 9;

    /** Ids in list order; the empty string is "none". */
    private final List<String> options = new ArrayList<>();
    private final Consumer<String> onPick;
    private final Runnable openManager;
    private int scroll;

    /**
     * @param onPick      gets the chosen preset id, or null for none; this screen has already
     *                    returned to {@code parent} when it runs
     * @param openManager opens the preset manager, for when there is nothing to pick
     */
    public StylePresetPickerScreen(Screen parent, int count, Consumer<String> onPick, Runnable openManager) {
        super(parent, Component.translatable("editor.historystages.graph.preset.pick.title", count));
        this.onPick = onPick;
        this.openManager = openManager;
        options.add("");
        for (Map.Entry<String, GraphStageData.Preset> e : GraphStageData.get().presetsByName()) options.add(e.getKey());
    }

    private boolean noPresets() {
        return options.size() == 1;
    }

    @Override
    protected int dialogWidth() { return 240; }

    /** Constant on purpose — the box is laid out once, in init(). */
    @Override
    protected int contentHeight() {
        return noPresets() ? 30 : Math.min(options.size(), MAX_VISIBLE) * ROW_H;
    }

    @Override
    protected Component confirmLabel() {
        return noPresets()
                ? Component.translatable("editor.historystages.graph.preset.pick.manage")
                : Component.translatable("editor.historystages.cancel");
    }

    @Override
    protected boolean showCancelButton() {
        return noPresets();
    }

    @Override
    protected boolean confirmOnEnter() {
        return false;
    }

    @Override
    protected void onConfirm() {
        if (noPresets()) {
            openManager.run();
            return;
        }
        this.minecraft.setScreen(parent);
    }

    @Override
    protected void renderContent(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
        if (noPresets()) {
            for (var line : this.font.split(Component.translatable("editor.historystages.graph.preset.pick.empty"), w)) {
                g.drawCenteredString(this.font, line, x + w / 2, y + 4, 0xFF999999);
                y += 10;
            }
            return;
        }
        int visible = Math.min(options.size(), MAX_VISIBLE);
        for (int i = 0; i < visible; i++) {
            int index = i + scroll;
            if (index >= options.size()) break;
            String id = options.get(index);
            int rowY = y + i * ROW_H;
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= rowY && mouseY < rowY + ROW_H;
            if (hovered) {
                g.fill(x, rowY, x + w, rowY + ROW_H, 0x25FFCC00);
                g.fill(x, rowY, x + 1, rowY + ROW_H, 0xFFFFCC00);
            }
            if (id.isEmpty()) {
                g.drawString(this.font, Component.translatable("editor.historystages.graph.preset.none"),
                        x + 22, rowY + 5, 0xFF999999, false);
            } else {
                PresetSwatch.draw(g, id, x + 10, rowY + ROW_H / 2, 6);
                g.drawString(this.font, GraphStageData.get().presets().get(id).name,
                        x + 22, rowY + 5, hovered ? 0xFFFFCC00 : 0xFFEEEEEE, false);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isOpenSettled()) return true;
        if (!noPresets() && button == 0) {
            int x = boxX + PAD;
            int w = boxW - PAD * 2;
            int visible = Math.min(options.size(), MAX_VISIBLE);
            if (mouseX >= x && mouseX < x + w && mouseY >= contentY && mouseY < contentY + visible * ROW_H) {
                int index = (int) ((mouseY - contentY) / ROW_H) + scroll;
                if (index < options.size()) {
                    Minecraft.getInstance().getSoundManager()
                            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    String id = options.get(index);
                    this.minecraft.setScreen(parent);
                    onPick.accept(id.isEmpty() ? null : id);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, options.size() - MAX_VISIBLE);
        scroll = (int) Math.max(0, Math.min(max, scroll - Math.signum(scrollY)));
        return true;
    }
}
