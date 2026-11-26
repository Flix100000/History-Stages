package net.bananemdnsa.historystages.platform.event.client;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * A container screen being drawn, raised at the two points NeoForge raises it; see
 * {@code mixin/client/ContainerScreenRenderMixin}.
 */
public abstract class ContainerScreenEvent extends Event {

    private final AbstractContainerScreen<?> screen;

    protected ContainerScreenEvent(AbstractContainerScreen<?> screen) {
        this.screen = screen;
    }

    public AbstractContainerScreen<?> getContainerScreen() {
        return screen;
    }

    public abstract static class Render extends ContainerScreenEvent {

        private final GuiGraphicsExtractor guiGraphics;
        private final int mouseX;
        private final int mouseY;

        protected Render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
            super(screen);
            this.guiGraphics = guiGraphics;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
        }

        public GuiGraphicsExtractor getGuiGraphics() {
            return guiGraphics;
        }

        public int getMouseX() {
            return mouseX;
        }

        public int getMouseY() {
            return mouseY;
        }

        /** After the background texture, before the slots. Screen coordinates. */
        public static class Background extends Render {
            public Background(AbstractContainerScreen<?> screen, GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
                super(screen, guiGraphics, mouseX, mouseY);
            }
        }

        /** After the slots and labels, before the carried item. Coordinates relative to the GUI's corner. */
        public static class Foreground extends Render {
            public Foreground(AbstractContainerScreen<?> screen, GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
                super(screen, guiGraphics, mouseX, mouseY);
            }
        }
    }
}
