package net.bananemdnsa.historystages.client.editor.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * An {@link EditBox} with the input filter 26.2 removed from vanilla: text the filter rejects is
 * never set or inserted, as with 1.21.1's {@code EditBox.setFilter}.
 */
public class FilteredEditBox extends EditBox {

    private Predicate<String> filter = Objects::nonNull;

    public FilteredEditBox(Font font, int x, int y, int width, int height, Component narration) {
        super(font, x, y, width, height, narration);
    }

    public void setFilter(Predicate<String> filter) {
        this.filter = filter;
    }

    @Override
    public void setValue(String value) {
        if (filter.test(value)) super.setValue(value);
    }

    @Override
    public void insertText(String input) {
        String before = getValue();
        int cursor = getCursorPosition();
        super.insertText(input);
        if (!filter.test(getValue())) {
            // Put the old text back; vanilla 1.21.1 checked before writing, the effect is the same.
            super.setValue(before);
            setCursorPosition(cursor);
            setHighlightPos(cursor);
        }
    }
}
