package net.bananemdnsa.historystages.platform.event.client;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.bananemdnsa.historystages.platform.bus.ICancellableEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.Consumer;

/** Things that happen to a screen that is already open, or is just being built. */
public abstract class ScreenEvent extends Event {

    private final Screen screen;

    protected ScreenEvent(Screen screen) {
        this.screen = screen;
    }

    public Screen getScreen() {
        return screen;
    }

    /** A screen being built. */
    public abstract static class Init extends ScreenEvent {

        private final Consumer<GuiEventListener> adder;

        protected Init(Screen screen, Consumer<GuiEventListener> adder) {
            super(screen);
            this.adder = adder;
        }

        /**
         * Adds a widget to the screen. Handed in rather than reached for, because putting a widget
         * onto someone else's screen needs the screen's own list, and a handler has no business
         * knowing how that is got at.
         */
        public void addListener(GuiEventListener listener) {
            adder.accept(listener);
        }

        /** After the screen has built its own widgets, so an added one is not overwritten. */
        public static class Post extends Init {
            public Post(Screen screen, Consumer<GuiEventListener> adder) {
                super(screen, adder);
            }
        }
    }

    /** A screen being drawn. */
    public abstract static class Render extends ScreenEvent {

        private final GuiGraphicsExtractor graphics;
        private final int mouseX;
        private final int mouseY;
        private final float partialTick;

        protected Render(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            super(screen);
            this.graphics = graphics;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.partialTick = partialTick;
        }

        public GuiGraphicsExtractor getGuiGraphics() {
            return graphics;
        }

        public int getMouseX() {
            return mouseX;
        }

        public int getMouseY() {
            return mouseY;
        }

        public float getPartialTick() {
            return partialTick;
        }

        /** After the screen has drawn itself, which is where an overlay belongs. */
        public static class Post extends Render {
            public Post(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
                super(screen, graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    /** A mouse button going down on a screen. */
    public abstract static class MouseButtonPressed extends ScreenEvent {

        private final double mouseX;
        private final double mouseY;
        private final int button;

        protected MouseButtonPressed(Screen screen, double mouseX, double mouseY, int button) {
            super(screen);
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.button = button;
        }

        public double getMouseX() {
            return mouseX;
        }

        public double getMouseY() {
            return mouseY;
        }

        public int getButton() {
            return button;
        }

        /** Before the screen sees it. Cancelling means the screen never does. */
        public static class Pre extends MouseButtonPressed implements ICancellableEvent {
            public Pre(Screen screen, double mouseX, double mouseY, int button) {
                super(screen, mouseX, mouseY, button);
            }
        }
    }

    public abstract static class KeyPressed extends ScreenEvent {

        private final int keyCode;
        private final int scanCode;
        private final int modifiers;

        protected KeyPressed(Screen screen, int keyCode, int scanCode, int modifiers) {
            super(screen);
            this.keyCode = keyCode;
            this.scanCode = scanCode;
            this.modifiers = modifiers;
        }

        public int getKeyCode() {
            return keyCode;
        }

        public int getScanCode() {
            return scanCode;
        }

        public int getModifiers() {
            return modifiers;
        }

        public static class Pre extends KeyPressed implements ICancellableEvent {
            public Pre(Screen screen, int keyCode, int scanCode, int modifiers) {
                super(screen, keyCode, scanCode, modifiers);
            }
        }
    }

    /** Before a screen is shown. Cancelling keeps the current one; closing screens is not reported. */
    public static class Opening extends ScreenEvent implements ICancellableEvent {

        private final @org.jetbrains.annotations.Nullable Screen newScreen;

        public Opening(@org.jetbrains.annotations.Nullable Screen currentScreen,
                       @org.jetbrains.annotations.Nullable Screen newScreen) {
            super(currentScreen);
            this.newScreen = newScreen;
        }

        public @org.jetbrains.annotations.Nullable Screen getCurrentScreen() {
            return getScreen();
        }

        public @org.jetbrains.annotations.Nullable Screen getNewScreen() {
            return newScreen;
        }
    }
}
