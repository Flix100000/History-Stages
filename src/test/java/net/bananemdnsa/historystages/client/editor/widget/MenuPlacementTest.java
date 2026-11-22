package net.bananemdnsa.historystages.client.editor.widget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #142: a right-click on the last rows of a long list opened the menu below the screen edge, so
 * its last entry ("Remove") could not be clicked.
 */
class MenuPlacementTest {

    private static final int SCREEN = 240;
    private static final int MENU = 72;

    @Test
    void opensAtTheCursorWhenThereIsRoom() {
        assertEquals(50, MenuPlacement.fit(50, MENU, SCREEN));
        assertEquals(SCREEN - MENU, MenuPlacement.fit(SCREEN - MENU, MENU, SCREEN),
                "a menu that ends exactly on the edge still fits");
    }

    @Test
    void flipsUpwardsNearTheBottomSoTheLastEntryStaysOnScreen() {
        int anchor = 230;
        int start = MenuPlacement.fit(anchor, MENU, SCREEN);
        assertEquals(anchor - MENU, start, "the menu should end where the cursor is");
        assertTrue(start + MENU <= SCREEN);
    }

    @Test
    void menuTallerThanBothSidesIsPushedBackOnScreen() {
        int start = MenuPlacement.fit(40, 100, 120);
        assertEquals(20, start);
        assertTrue(start >= 0 && start + 100 <= 120);
    }

    @Test
    void menuTallerThanTheScreenKeepsItsTopReachable() {
        assertEquals(0, MenuPlacement.fit(30, 300, SCREEN));
    }

    @Test
    void clampKeepsARightAlignedMenuInside() {
        assertEquals(0, MenuPlacement.clamp(-15, MENU, SCREEN));
        assertEquals(SCREEN - MENU, MenuPlacement.clamp(200, MENU, SCREEN));
        assertEquals(100, MenuPlacement.clamp(100, MENU, SCREEN));
    }
}
