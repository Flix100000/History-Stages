package net.bananemdnsa.historystages.client.editor.widget;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The responder only ever hears text the filter accepted. Screens treat every responder call as
 * a user edit (unsaved-changes prompt, live validation), so a rejected keystroke must stay silent.
 */
class FilteredEditBoxTest {

    private static FilteredEditBox digitsOnly(List<String> heard) {
        FilteredEditBox box = new FilteredEditBox(null, 0, 0, 100, 20, Component.empty());
        box.setFilter(s -> s.chars().allMatch(Character::isDigit));
        box.setValue("12");
        box.setResponder(heard::add);
        return box;
    }

    @Test
    void rejectedInsertIsNeverHeardAndLeavesTheText() {
        List<String> heard = new ArrayList<>();
        FilteredEditBox box = digitsOnly(heard);
        box.insertText("a");
        assertEquals("12", box.getValue());
        assertEquals(List.of(), heard);
    }

    @Test
    void acceptedInsertIsHeardOnce() {
        List<String> heard = new ArrayList<>();
        FilteredEditBox box = digitsOnly(heard);
        box.insertText("3");
        assertEquals("123", box.getValue());
        assertEquals(List.of("123"), heard);
    }

    @Test
    void deleteIsFilteredToo() {
        List<String> heard = new ArrayList<>();
        FilteredEditBox box = new FilteredEditBox(null, 0, 0, 100, 20, Component.empty());
        box.setFilter(s -> !s.isEmpty());
        box.setValue("7");
        box.setResponder(heard::add);
        box.deleteChars(-1);
        assertEquals("7", box.getValue());
        assertEquals(List.of(), heard);
    }
}
