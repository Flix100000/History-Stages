package net.bananemdnsa.historystages.fabric.gametest;

import net.bananemdnsa.historystages.gametest.RecipeListSyncScenarios;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public class RecipeListSyncTests {

    @GameTest(skyAccess = true)
    public void theRecipeListReadsBackWhole(GameTestHelper helper) {
        RecipeListSyncScenarios.theRecipeListReadsBackWhole(helper);
    }

    @GameTest(skyAccess = true)
    public void onlyEditorsGetTheList(GameTestHelper helper) {
        RecipeListSyncScenarios.onlyEditorsGetTheList(helper, TestPlayers::createConnected);
    }
}
