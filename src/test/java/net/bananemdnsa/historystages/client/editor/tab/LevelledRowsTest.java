package net.bananemdnsa.historystages.client.editor.tab;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.data.LevelledLockEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LevelledRowsTest {

    private static final String SHARPNESS = "minecraft:sharpness";
    private static final String MENDING = "minecraft:mending";

    @Test
    void loadingKeepsEverySettingPerId() {
        LevelledRows rows = new LevelledRows();
        List<String> ids = new ArrayList<>();
        rows.load(List.of(new LevelledLockEntry(SHARPNESS, 4, null, null),
                new LevelledLockEntry(MENDING, null, List.of("anvil"), List.of("minecraft:book"))), ids);

        assertEquals(List.of(SHARPNESS, MENDING), ids);
        assertEquals(4, rows.minLevel(SHARPNESS));
        assertEquals(List.of("anvil"), rows.lockActions(MENDING));
        assertEquals(List.of("minecraft:book"), rows.excludedItemTypes(MENDING));
    }

    @Test
    void aNewRowLocksEverything() {
        LevelledRows rows = new LevelledRows();
        assertNull(rows.minLevel(SHARPNESS));
        assertNull(rows.lockActions(SHARPNESS));
        assertNull(rows.excludedItemTypes(SHARPNESS));
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

    /** Changing one setting keeps the others. */
    @Test
    void settingsAreIndependent() {
        LevelledRows rows = new LevelledRows();
        rows.setMinLevel(SHARPNESS, 4);
        rows.setLockActions(SHARPNESS, List.of("pickup"));
        rows.setExcludedItemTypes(SHARPNESS, List.of("minecraft:book"));
        assertEquals(4, rows.minLevel(SHARPNESS));
        assertEquals(List.of("pickup"), rows.lockActions(SHARPNESS));
        assertEquals(List.of("minecraft:book"), rows.excludedItemTypes(SHARPNESS));
    }

    @Test
    void entriesAreRebuiltInRowOrderAndRemovalForgets() {
        LevelledRows rows = new LevelledRows();
        List<String> ids = new ArrayList<>(List.of(MENDING, SHARPNESS));
        rows.setMinLevel(SHARPNESS, 4);
        rows.setLockActions(MENDING, List.of("anvil"));

        List<LevelledLockEntry> built = rows.toEntries(ids);
        assertEquals(MENDING, built.get(0).getId());
        assertEquals(List.of("anvil"), built.get(0).getLockActions());
        assertEquals(4, built.get(1).getMinLevel());

        rows.forget(SHARPNESS);
        assertNull(rows.minLevel(SHARPNESS));
    }
}
