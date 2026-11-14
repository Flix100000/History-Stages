package net.bananemdnsa.historystages.client.editor.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * An {@link EditBox} with the input filter 26.2 removed from vanilla: text the filter rejects is
 * never set, inserted or left behind by a delete, as with 1.21.1's {@code EditBox.setFilter}.
 *
 * <p>Vanilla writes the text first and tells the responder straight away, so the check has to sit
 * between the two: the responder is held back while vanilla edits and only hears the result once
 * the filter has accepted it. Otherwise it would see a value that is reverted a moment later and
 * mark a screen as changed or a field as invalid.
 */
public class FilteredEditBox extends EditBox {

    private Predicate<String> filter = Objects::nonNull;
    private @Nullable Consumer<String> responder;
    private boolean holdResponder;

    public FilteredEditBox(@Nullable Font font, int x, int y, int width, int height, Component narration) {
        super(font, x, y, width, height, narration);
        super.setResponder(this::forward);
    }

    public void setFilter(Predicate<String> filter) {
        this.filter = filter;
    }

    @Override
    public void setResponder(Consumer<String> responder) {
        this.responder = responder;
    }

    private void forward(String value) {
        if (!holdResponder && responder != null) responder.accept(value);
    }

    @Override
    public void setValue(String value) {
        if (filter.test(value)) super.setValue(value);
    }

    @Override
    public void insertText(String input) {
        guarded(() -> super.insertText(input));
    }

    @Override
    public void deleteCharsToPos(int pos) {
        guarded(() -> super.deleteCharsToPos(pos));
    }

    private void guarded(Runnable edit) {
        String before = getValue();
        int cursor = getCursorPosition();
        boolean outer = !holdResponder;
        holdResponder = true;
        try {
            edit.run();
            if (!filter.test(getValue())) {
                super.setValue(before);
                setCursorPosition(cursor);
                setHighlightPos(cursor);
            }
        } finally {
            if (outer) holdResponder = false;
        }
        if (outer && !getValue().equals(before)) forward(getValue());
    }
}
