package net.bananemdnsa.historystages.client.editor.recipe;

import java.util.List;
import java.util.Set;

import net.bananemdnsa.historystages.client.editor.recipe.IngredientUsage.Kind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which recipes the bulk-lock popup files under "only locked" and which under "works without".
 * The popup ticks the first group and leaves the second unticked, so a wrong answer here is a
 * recipe the user locks without meaning to.
 */
class IngredientUsageTest {

    private static final String IRON = "minecraft:iron_ingot";
    private static final String IRON_BLOCK = "minecraft:iron_block";
    private static final String COBBLE = "minecraft:cobblestone";
    private static final String DEEPSLATE = "minecraft:cobbled_deepslate";

    @Test
    void slotTakingOnlyTheLockedItemIsOnlyLocked() {
        // heavy weighted pressure plate: two iron slots
        List<Set<String>> plate = List.of(Set.of(IRON), Set.of(IRON));
        assertEquals(Kind.ONLY_LOCKED, IngredientUsage.classify(plate, IRON, Set.of(IRON)));
    }

    @Test
    void slotWithAnUnlockedAlternativeWorksWithout() {
        // furnace: eight slots of the stone crafting tag
        Set<String> stone = Set.of(COBBLE, DEEPSLATE);
        List<Set<String>> furnace = List.of(stone, stone, stone, stone, stone, stone, stone, stone);
        assertEquals(Kind.WORKS_WITHOUT, IngredientUsage.classify(furnace, COBBLE, Set.of(COBBLE)));
    }

    @Test
    void alternativesThatAreAllLockedCountAsOnlyLocked() {
        List<Set<String>> recipe = List.of(Set.of(IRON, IRON_BLOCK));
        assertEquals(Kind.ONLY_LOCKED,
                IngredientUsage.classify(recipe, IRON, Set.of(IRON, IRON_BLOCK)));
    }

    @Test
    void oneExclusiveSlotIsEnoughEvenIfAnotherHasAlternatives() {
        List<Set<String>> recipe = List.of(Set.of(IRON, COBBLE), Set.of(IRON));
        assertEquals(Kind.ONLY_LOCKED, IngredientUsage.classify(recipe, IRON, Set.of(IRON)));
    }

    @Test
    void recipeWithoutTheItemIsNone() {
        List<Set<String>> recipe = List.of(Set.of(COBBLE));
        assertEquals(Kind.NONE, IngredientUsage.classify(recipe, IRON, Set.of(IRON)));
    }

    @Test
    void slotKindFollowsTheSameRule() {
        assertEquals(Kind.NONE, IngredientUsage.slotKind(Set.of(COBBLE), IRON, Set.of(IRON)));
        assertEquals(Kind.ONLY_LOCKED, IngredientUsage.slotKind(Set.of(IRON), IRON, Set.of(IRON)));
        assertEquals(Kind.WORKS_WITHOUT,
                IngredientUsage.slotKind(Set.of(IRON, COBBLE), IRON, Set.of(IRON)));
    }

    @Test
    void requiresLockedIgnoresEmptySlots() {
        assertFalse(IngredientUsage.requiresLocked(List.of(Set.of()), Set.of(IRON)));
        assertTrue(IngredientUsage.requiresLocked(List.of(Set.of(), Set.of(IRON_BLOCK)),
                Set.of(IRON, IRON_BLOCK)));
        assertFalse(IngredientUsage.requiresLocked(List.of(Set.of(IRON, COBBLE)), Set.of(IRON)));
    }

    @Test
    void resultIsOnlyLockedWhenEveryRecipeNeedsLockedMaterial() {
        // bucket with a second recipe that needs no iron at all
        assertEquals(Kind.WORKS_WITHOUT, IngredientUsage.forResult(List.of(true, false)));
        assertEquals(Kind.ONLY_LOCKED, IngredientUsage.forResult(List.of(true, true)));
        assertEquals(Kind.NONE, IngredientUsage.forResult(List.of()));
    }
}
