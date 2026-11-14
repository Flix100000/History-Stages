package net.bananemdnsa.historystages.neoforge.gametest;

import java.util.Map;
import java.util.function.Consumer;

import net.bananemdnsa.historystages.gametest.DisguiseBreakScenarios;
import net.bananemdnsa.historystages.gametest.InteractionScenarios;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The GameTest scenarios in common, run through NeoForge's events. Fabric lists the same ones as
 * annotated methods.
 */
public final class CommonScenarioTests {

    private CommonScenarioTests() {}

    static void collect(Map<String, Consumer<GameTestHelper>> tests) {
        tests.put("disguise/ore_drops_the_disguise",
                helper -> DisguiseBreakScenarios.dropsTheDisguise(helper, TestPlayers::createConnected));
        tests.put("disguise/ore_harvests_like_the_disguise",
                helper -> DisguiseBreakScenarios.harvestFollowsTheDisguise(helper, TestPlayers::createConnected));
        tests.put("disguise/ore_breaks_at_the_disguises_speed",
                helper -> DisguiseBreakScenarios.breaksAtTheDisguisesSpeed(helper, TestPlayers::createConnected));
        tests.put("interaction/only_saddleable_mobs_are_mounted", InteractionScenarios::onlySaddleableMobsAreMounted);
        tests.put("recipe/a_recipe_without_display_still_has_its_output",
                net.bananemdnsa.historystages.gametest.RecipeOutputScenarios::aRecipeWithoutDisplayStillHasItsOutput);
        tests.put("network/a_big_stage_save_fits_the_serverbound_limit",
                net.bananemdnsa.historystages.gametest.PayloadSizeScenarios::aBigStageSaveFitsTheServerboundLimit);
    }
}
