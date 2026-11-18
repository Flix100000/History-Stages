package net.bananemdnsa.historystages.compat;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewerCategoryExemptionsTest {

    @Test
    void jerIsAlwaysExempt() {
        assertTrue(ViewerCategoryExemptions.isExempt("jeresources:mob", List.of()));
        assertTrue(ViewerCategoryExemptions.isExempt("jeresources:dungeon", null));
    }

    @Test
    void nothingElseIsExemptByDefault() {
        assertFalse(ViewerCategoryExemptions.isExempt("minecraft:crafting", List.of()));
    }

    @Test
    void aFullIdNamesOneCategory() {
        List<String> configured = List.of("create:mixing");
        assertTrue(ViewerCategoryExemptions.isExempt("create:mixing", configured));
        assertFalse(ViewerCategoryExemptions.isExempt("create:pressing", configured));
    }

    @Test
    void aModIdNamesEveryCategoryOfThatMod() {
        List<String> configured = List.of("create");
        assertTrue(ViewerCategoryExemptions.isExempt("create:mixing", configured));
        assertTrue(ViewerCategoryExemptions.isExempt("create:pressing", configured));
        assertFalse(ViewerCategoryExemptions.isExempt("createaddition:charging", configured));
    }

    @Test
    void blanksAndStraySpacesAreForgiven() {
        assertTrue(ViewerCategoryExemptions.isExempt("create:mixing", List.of("", "  create:mixing ")));
        assertFalse(ViewerCategoryExemptions.isExempt("minecraft:crafting", List.of(" ")));
    }
}
