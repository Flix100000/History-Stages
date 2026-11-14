package net.bananemdnsa.historystages.fabric.gametest;

import net.bananemdnsa.historystages.gametest.InteractionScenarios;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public class InteractionClassifyTests {

    @GameTest(skyAccess = true)
    public void onlySaddleableMobsAreMounted(GameTestHelper helper) {
        InteractionScenarios.onlySaddleableMobsAreMounted(helper);
    }
}
