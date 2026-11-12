package net.bananemdnsa.historystages.data.lock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The audit result is kept whole, because the debug report re-reads it on every registry pass —
 * a /history reload clears the report, but does not re-apply recipes.
 */
class MissingRecipeIdsTest {

    @AfterEach
    void reset() {
        MissingRecipeIds.set(List.of());
    }

    @Test
    void theLastAuditIsKeptWithTheStageThatGatesEachRecipe() {
        var entry = new RecipeIdAudit.MissingRecipe("kubejs", "kubejs:crafting/steam_engine");
        MissingRecipeIds.set(List.of(entry));

        assertEquals(List.of(entry), MissingRecipeIds.entries());
        assertTrue(MissingRecipeIds.isMissing("kubejs:crafting/steam_engine"));
    }

    @Test
    void theWarningNamesStageAndRecipe() {
        var entry = new RecipeIdAudit.MissingRecipe("kubejs", "kubejs:crafting/steam_engine");
        assertEquals("Stage 'kubejs' gates recipe 'kubejs:crafting/steam_engine', which is not loaded. "
                + "The lock does nothing. Script-generated ids change when the script is reordered.", entry.message());
    }
}
