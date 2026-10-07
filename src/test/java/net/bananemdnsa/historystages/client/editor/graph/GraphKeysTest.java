package net.bananemdnsa.historystages.client.editor.graph;

import net.bananemdnsa.historystages.client.editor.graph.GraphKeys.Action;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GraphKeysTest {

    // GLFW key codes, spelled out so the test needs no LWJGL.
    private static final int KEY_Y = 89, KEY_Z = 90, KEY_A = 65, KEY_D = 68, KEY_F = 70;
    private static final int KEY_RIGHT_BRACKET = 93, KEY_SLASH = 47, KEY_MINUS = 45, KEY_0 = 48, KEY_EQUAL = 61;

    @Test
    void onQwertzTheKeyLabelledZUndoes() {
        // A German keyboard reports the key labelled Z at the US Y position.
        assertEquals(Action.UNDO, GraphKeys.resolve(KEY_Y, "z", true, false));
    }

    @Test
    void onQwertzTheKeyLabelledYRedoes() {
        assertEquals(Action.REDO, GraphKeys.resolve(KEY_Z, "y", true, false));
    }

    @Test
    void ctrlShiftZRedoes() {
        assertEquals(Action.REDO, GraphKeys.resolve(KEY_Z, "z", true, true));
    }

    @Test
    void withoutAKeyNameTheUsPositionIsUsed() {
        assertEquals(Action.UNDO, GraphKeys.resolve(KEY_Z, null, true, false));
        assertEquals(Action.REDO, GraphKeys.resolve(KEY_Y, null, true, false));
    }

    @Test
    void lettersNeedCtrl() {
        assertNull(GraphKeys.resolve(KEY_Z, "z", false, false));
        assertNull(GraphKeys.resolve(KEY_A, "a", false, false));
    }

    @Test
    void ctrlLettersForSelectionAndSearch() {
        assertEquals(Action.SELECT_ALL, GraphKeys.resolve(KEY_A, "a", true, false));
        assertEquals(Action.SELECT_NONE, GraphKeys.resolve(KEY_D, "d", true, false));
        assertEquals(Action.SEARCH, GraphKeys.resolve(KEY_F, "f", true, false));
    }

    @Test
    void plusIsFoundByItsLabelWhereverTheLayoutPutsIt() {
        // German '+' sits at the US ']' position.
        assertEquals(Action.ZOOM_IN, GraphKeys.resolve(KEY_RIGHT_BRACKET, "+", false, false));
        assertEquals(Action.ZOOM_IN, GraphKeys.resolve(GraphKeys.KP_ADD, null, false, false));
        assertEquals(Action.ZOOM_IN, GraphKeys.resolve(KEY_EQUAL, "=", false, false));
    }

    @Test
    void minusAndZeroByLabelAndNumpad() {
        // German '-' sits at the US '/' position.
        assertEquals(Action.ZOOM_OUT, GraphKeys.resolve(KEY_SLASH, "-", false, false));
        assertEquals(Action.ZOOM_OUT, GraphKeys.resolve(GraphKeys.KP_SUBTRACT, null, false, false));
        assertEquals(Action.ZOOM_RESET, GraphKeys.resolve(KEY_0, "0", false, false));
        assertEquals(Action.ZOOM_RESET, GraphKeys.resolve(GraphKeys.KP_0, null, false, false));
    }

    @Test
    void aKeyWhoseLabelIsSomethingElseIsNotMistakenForItsUsMeaning() {
        // On QWERTZ the US '-' position is labelled 'ß'.
        assertNull(GraphKeys.resolve(KEY_MINUS, "ß", false, false));
    }

    @Test
    void namedKeys() {
        assertEquals(Action.FIT, GraphKeys.resolve(GraphKeys.SPACE, null, false, false));
        assertEquals(Action.HELP, GraphKeys.resolve(GraphKeys.F1, null, false, false));
        assertEquals(Action.REMOVE, GraphKeys.resolve(GraphKeys.DELETE, null, false, false));
    }

    @Test
    void ctrlWithANonLetterIsNotAShortcut() {
        assertNull(GraphKeys.resolve(GraphKeys.SPACE, null, true, false));
        assertNull(GraphKeys.resolve(GraphKeys.DELETE, null, true, false));
    }
}
