package net.bananemdnsa.historystages.fabric.gametest;

import net.bananemdnsa.historystages.gametest.PayloadSizeScenarios;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public class PayloadSizeTests {

    @GameTest(skyAccess = true)
    public void aBigStageSaveFitsTheServerboundLimit(GameTestHelper helper) {
        PayloadSizeScenarios.aBigStageSaveFitsTheServerboundLimit(helper);
    }
}
