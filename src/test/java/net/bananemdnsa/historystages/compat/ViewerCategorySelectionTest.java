package net.bananemdnsa.historystages.compat;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewerCategorySelectionTest {

    @Test
    void readsModsAndCategoriesApart() {
        ViewerCategorySelection s = new ViewerCategorySelection(List.of("create", "minecraft:crafting"));
        assertTrue(s.isWholeMod("create"));
        assertTrue(s.isTicked("create:mixing"));
        assertTrue(s.isTicked("minecraft:crafting"));
        assertFalse(s.isTicked("minecraft:smelting"));
    }

    /** An entry the picker cannot show, because that mod is not installed, survives a save. */
    @Test
    void entriesForMissingModsSurvive() {
        ViewerCategorySelection s = new ViewerCategorySelection(List.of("mekanism", "thermal:press"));
        s.setCategory("minecraft:crafting", true);
        assertEquals(List.of("mekanism", "minecraft:crafting", "thermal:press"), s.toConfig());
    }

    /** A ticked mod covers its categories; unticking it brings the single ticks back. */
    @Test
    void aWholeModSwallowsItsCategoriesUntilUnticked() {
        ViewerCategorySelection s = new ViewerCategorySelection(List.of("create:mixing"));
        s.setWholeMod("create", true);
        assertEquals(List.of("create"), s.toConfig());
        s.setWholeMod("create", false);
        assertEquals(List.of("create:mixing"), s.toConfig());
    }

    @Test
    void blanksAreDropped() {
        assertEquals(List.of(), new ViewerCategorySelection(List.of("", "  ")).toConfig());
    }
}
