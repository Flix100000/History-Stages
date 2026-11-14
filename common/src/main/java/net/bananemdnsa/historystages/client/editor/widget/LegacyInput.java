package net.bananemdnsa.historystages.client.editor.widget;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

/**
 * Forwards input to vanilla widgets with 1.21.1's argument lists. 26.2 widgets take event records;
 * the editor code that hands clicks and keys on keeps its old shape through these.
 */
public final class LegacyInput {

    private LegacyInput() {}

    public static MouseButtonEvent mouse(double x, double y, int button) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0));
    }

    public static boolean mouseClicked(GuiEventListener target, double x, double y, int button) {
        return target.mouseClicked(mouse(x, y, button), false);
    }

    public static boolean mouseReleased(GuiEventListener target, double x, double y, int button) {
        return target.mouseReleased(mouse(x, y, button));
    }

    public static boolean mouseDragged(GuiEventListener target, double x, double y, int button, double dx, double dy) {
        return target.mouseDragged(mouse(x, y, button), dx, dy);
    }

    public static boolean keyPressed(GuiEventListener target, int key, int scancode, int modifiers) {
        return target.keyPressed(new KeyEvent(key, scancode, modifiers));
    }

    public static boolean charTyped(GuiEventListener target, char c, int modifiers) {
        return target.charTyped(new CharacterEvent(c));
    }

    /**
     * 1.21.1 handed a character outside the BMP to {@code charTyped(char, int)} as its two
     * surrogates, one call each; a plain {@code (char)} cast would cut it to garbage.
     */
    public static boolean eachChar(int codepoint, java.util.function.IntPredicate perChar) {
        if (Character.isBmpCodePoint(codepoint)) return perChar.test(codepoint);
        if (!Character.isValidCodePoint(codepoint)) return false;
        boolean handled = false;
        for (char c : Character.toChars(codepoint)) handled |= perChar.test(c);
        return handled;
    }
}
