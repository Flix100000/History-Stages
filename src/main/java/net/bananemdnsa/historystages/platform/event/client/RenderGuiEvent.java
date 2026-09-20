package net.bananemdnsa.historystages.platform.event.client;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;

/** The heads-up display, after vanilla has drawn it. */
@Environment(EnvType.CLIENT)
public abstract class RenderGuiEvent extends Event {

    private final GuiGraphics graphics;

    protected RenderGuiEvent(GuiGraphics graphics) {
        this.graphics = graphics;
    }

    public GuiGraphics getGuiGraphics() {
        return graphics;
    }

    public static class Post extends RenderGuiEvent {
        public Post(GuiGraphics graphics) {
            super(graphics);
        }
    }
}
