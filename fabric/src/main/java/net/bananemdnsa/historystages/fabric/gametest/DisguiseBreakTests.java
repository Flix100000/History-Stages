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

    @GameTest(skyAccess = true)
    public void disguiseSideEffectsStayOut(GameTestHelper helper) {
        DisguiseBreakScenarios.disguiseSideEffectsStayOut(helper, TestPlayers::createConnected);
    }

    /** Other Fabric mods hook drops at Block.popResource; a plain break must still go through it. */
    @GameTest(skyAccess = true)
    public void aPlainBreakKeepsVanillasDropPath(GameTestHelper helper) {
        net.bananemdnsa.historystages.fabric.events.PopResourceProbe.start();
        java.util.List<net.minecraft.world.item.Item> drops;
        int calls;
        try {
            drops = DisguiseBreakScenarios.breakPlainStone(helper, TestPlayers.createConnected(helper));
        } finally {
            calls = net.bananemdnsa.historystages.fabric.events.PopResourceProbe.stop();
        }
        helper.assertTrue(drops.contains(net.minecraft.world.item.Items.COBBLESTONE), "plain stone dropped " + drops);
        helper.assertTrue(calls > 0, "a plain break bypassed Block.popResource, so other mods' drop hooks never ran");
        helper.succeed();
    }
}
