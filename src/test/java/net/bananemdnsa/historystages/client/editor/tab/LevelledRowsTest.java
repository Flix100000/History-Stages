package net.bananemdnsa.historystages.client.editor.tab;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.data.LevelledLockEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelledRowsTest {

    private static final String SHARPNESS = "minecraft:sharpness";
    private static final String MENDING = "minecraft:mending";

    @Test
    void loadingKeepsLevelAndSwitchPerId() {
        LevelledRows rows = new LevelledRows();
        List<String> ids = new ArrayList<>();
        rows.load(List.of(new LevelledLockEntry(SHARPNESS, 4, true),
                new LevelledLockEntry(MENDING, null, false)), ids);

        assertEquals(List.of(SHARPNESS, MENDING), ids);
        assertEquals(4, rows.minLevel(SHARPNESS));
        assertFalse(rows.lockItems(MENDING));
    }

    @Test
    void aNewRowLocksEveryLevelAndItems() {
        LevelledRows rows = new LevelledRows();
        assertNull(rows.minLevel(SHARPNESS));
        assertTrue(rows.lockItems(SHARPNESS));
    }

    /** Level 1 is "every level", so it is stored as no minimum at all. */
    @Test
    void settingLevelOneClearsTheMinimum() {
        LevelledRows rows = new LevelledRows();
        rows.setMinLevel(SHARPNESS, 3);
        assertEquals(3, rows.minLevel(SHARPNESS));
        rows.setMinLevel(SHARPNESS, 1);
        assertNull(rows.minLevel(SHARPNESS));
    }

    @Test
    void toggleFlipsTheSwitch() {
        LevelledRows rows = new LevelledRows();
        rows.toggleLockItems(SHARPNESS);
        assertFalse(rows.lockItems(SHARPNESS));
        rows.toggleLockItems(SHARPNESS);
        assertTrue(rows.lockItems(SHARPNESS));
    }

    /** Stored in row order, and a removed row's settings do not come back with a re-add. */
    @Test
    void entriesAreRebuiltInRowOrderAndRemovalForgets() {
        LevelledRows rows = new LevelledRows();
        List<String> ids = new ArrayList<>(List.of(MENDING, SHARPNESS));
        rows.setMinLevel(SHARPNESS, 4);
        rows.toggleLockItems(MENDING);

        List<LevelledLockEntry> built = rows.toEntries(ids);
        assertEquals(MENDING, built.get(0).getId());
        assertFalse(built.get(0).isLockItems());
        assertEquals(4, built.get(1).getMinLevel());

        rows.forget(SHARPNESS);
        assertNull(rows.minLevel(SHARPNESS));
    }
}
