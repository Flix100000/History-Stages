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
        tests.put("disguise/side_effects_stay_out",
                helper -> DisguiseBreakScenarios.disguiseSideEffectsStayOut(helper, TestPlayers::createConnected));
        tests.put("interaction/only_saddleable_mobs_are_mounted", InteractionScenarios::onlySaddleableMobsAreMounted);
        tests.put("recipe/a_recipe_without_display_still_has_its_output",
                net.bananemdnsa.historystages.gametest.RecipeOutputScenarios::aRecipeWithoutDisplayStillHasItsOutput);
        tests.put("recipe/a_recipe_locked_by_id_is_locked",
                net.bananemdnsa.historystages.gametest.RecipeOutputScenarios::aRecipeLockedByIdIsLocked);
        tests.put("network/a_big_stage_save_fits_the_serverbound_limit",
                net.bananemdnsa.historystages.gametest.PayloadSizeScenarios::aBigStageSaveFitsTheServerboundLimit);
        tests.put("network/the_recipe_list_reads_back_whole",
                net.bananemdnsa.historystages.gametest.RecipeListSyncScenarios::theRecipeListReadsBackWhole);
        tests.put("network/the_same_recipes_encode_the_same",
                net.bananemdnsa.historystages.gametest.RecipeListSyncScenarios::theSameRecipesEncodeTheSame);
        tests.put("network/only_editors_get_the_recipe_list", helper ->
                net.bananemdnsa.historystages.gametest.RecipeListSyncScenarios.onlyEditorsGetTheList(helper, TestPlayers::createConnected));
        tests.put("slotlock/mod_insert_into_player_skips_locked_slot", helper ->
                net.bananemdnsa.historystages.gametest.SlotLockTransferScenarios.modInsertIntoPlayerSkipsLockedSlot(
                        helper, CommonScenarioTests::insertIntoPlayerSlot));
        tests.put("slotlock/sided_mod_insert_skips_locked_furnace_slot", helper ->
                net.bananemdnsa.historystages.gametest.SlotLockTransferScenarios.sidedModInsertSkipsLockedFurnaceSlot(
                        helper, CommonScenarioTests::insertSided));
    }

    /** What a pipe or a mod "give" helper does with one inventory slot on NeoForge. */
    private static long insertIntoPlayerSlot(net.minecraft.world.entity.player.Player player, int slot,
                                             net.minecraft.world.item.Item item, int count) {
        var handler = net.neoforged.neoforge.transfer.item.PlayerInventoryWrapper.of(player).getSlot(slot);
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            int inserted = handler.insert(net.neoforged.neoforge.transfer.item.ItemResource.of(item), count, tx);
            tx.commit();
            return inserted;
        }
    }

    /** The capability a pipe asks the block for, from one side. */
    private static long insertSided(net.minecraft.world.Container container, net.minecraft.core.Direction side,
                                    net.minecraft.world.item.Item item, int count) {
        var be = (net.minecraft.world.level.block.entity.BlockEntity) container;
        var handler = be.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK,
                be.getBlockPos(), side);
        if (handler == null) return -1;
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            int inserted = handler.insert(net.neoforged.neoforge.transfer.item.ItemResource.of(item), count, tx);
            tx.commit();
            return inserted;
        }
    }
}
