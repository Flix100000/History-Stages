package net.bananemdnsa.historystages.platform.event.client;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The heads-up display, after vanilla has drawn it. */
public abstract class RenderGuiEvent extends Event {

    private final GuiGraphicsExtractor graphics;

    protected RenderGuiEvent(GuiGraphicsExtractor graphics) {
        this.graphics = graphics;
    }

    public GuiGraphicsExtractor getGuiGraphics() {
        return graphics;
    }

    public static class Post extends RenderGuiEvent {
        public Post(GuiGraphicsExtractor graphics) {
            super(graphics);
        }
    }
}
