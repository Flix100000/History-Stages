package net.bananemdnsa.historystages.fabric.gametest;

import net.bananemdnsa.historystages.gametest.DisguiseBreakScenarios;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Disguised locked blocks break, harvest and drop as their disguise (via the Fabric mixins). */
public class DisguiseBreakTests {

    @GameTest(skyAccess = true)
    public void disguisedOreDropsTheDisguise(GameTestHelper helper) {
        DisguiseBreakScenarios.dropsTheDisguise(helper, TestPlayers::createConnected);
    }

    @GameTest(skyAccess = true)
    public void disguisedOreHarvestsLikeTheDisguise(GameTestHelper helper) {
        DisguiseBreakScenarios.harvestFollowsTheDisguise(helper, TestPlayers::createConnected);
    }

    @GameTest(skyAccess = true)
    public void disguisedOreBreaksAtTheDisguisesSpeed(GameTestHelper helper) {
        DisguiseBreakScenarios.breaksAtTheDisguisesSpeed(helper, TestPlayers::createConnected);
    }
}
