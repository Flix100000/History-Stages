package net.bananemdnsa.historystages.api.editor.widget;

import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * A {@link ChoiceOverlay} in a screen of its own.
 *
 * <p>Exists for the seam an addon actually reaches:
 * {@link net.bananemdnsa.historystages.api.editor.TriggerEditor#authoringScreen} hands back a
 * screen rather than opening an overlay, because the editor owns how its overlays are shown. So an
 * addon whose trigger is "one of these four things" had no way to say that without hand-rolling a
 * screen — the same gap {@link CountInputScreen} closed for "how many".
 *
 * <p>Picking an option, or pressing ESC, returns to {@code parent}. An option that opens another
 * screen of its own is free to do so; this one only steps back when nothing else took over.
 */
public class ChoiceScreen extends Screen {

    private static final int BACKDROP = 0xC0000000;

    private final Screen parent;
    private final ChoiceOverlay overlay;

    /**
     * @param parent  the screen to return to once a choice is made or cancelled
     * @param title   heading above the rows; also the screen's own title
     * @param options the rows, in the order they should appear
     */
    public ChoiceScreen(Screen parent, Component title, List<ChoiceOverlay.Option> options) {
        super(title);
        this.parent = parent;
        this.overlay = new ChoiceOverlay(title.getString(), options);
    }

    @Override
    protected void init() {
        overlay.show(this.width / 2, this.height / 2, this.width);
    }

    /** No-op: 1.21 would otherwise put the menu blur shader behind the panel. */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
    }

    public void renderBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        render(graphics, mouseX, mouseY, partialTick);
    }

    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, BACKDROP);
        overlay.render(g, this.font, mouseX, mouseY);
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        // The overlay hides itself the moment a row is picked. If that row opened a screen, this
        // one is no longer being rendered and the check never runs; if it did not, the choice is
        // finished and there is nothing left here to show.
        if (!overlay.isVisible() && this.minecraft != null && this.minecraft.gui.screen() == this) {
            this.minecraft.gui.setScreen(parent);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return mouseClicked(event.x(), event.y(), event.button());
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return overlay.mouseClicked(mouseX, mouseY) || super.mouseClicked(new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, 0)), false);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return keyPressed(event.key(), event.scancode(), event.modifiers());
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return overlay.keyPressed(keyCode) || super.keyPressed(new KeyEvent(keyCode, scanCode, modifiers));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
