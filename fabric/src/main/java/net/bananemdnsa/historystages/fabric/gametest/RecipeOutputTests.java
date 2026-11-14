package net.bananemdnsa.historystages.fabric.gametest;

import net.bananemdnsa.historystages.gametest.RecipeOutputScenarios;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public class RecipeOutputTests {

    @GameTest(skyAccess = true)
    public void aRecipeWithoutDisplayStillHasItsOutput(GameTestHelper helper) {
        RecipeOutputScenarios.aRecipeWithoutDisplayStillHasItsOutput(helper);
    }
}
