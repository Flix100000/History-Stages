package net.bananemdnsa.historystages.gametest;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The stonecutter in 26.2 takes its buttons from a precomputed set and its result straight from
 * the chosen entry, past every recipe lookup the filter gates (new for the port; 1.21.1 was
 * covered through {@code getRecipesFor}).
 */
public final class StonecutterLockTests {

    private static final String LOCKED_RECIPE = "minecraft:stone_bricks_from_stone_stonecutting";

    private StonecutterLockTests() {}

    /** Puts stone in a fresh stonecutter for this player and clicks the locked recipe's button. */
    private static ItemStack cutStoneBricks(GameTestHelper helper, ServerPlayer player) {
        StonecutterMenu menu = new StonecutterMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
        menu.getSlot(StonecutterMenu.INPUT_SLOT).set(new ItemStack(Items.STONE));
        var entries = menu.getVisibleRecipes().entries();
        for (int i = 0; i < entries.size(); i++) {
            var recipe = entries.get(i).recipe().recipe();
            if (recipe.isPresent() && recipe.get().id().identifier().toString().equals(LOCKED_RECIPE)) {
                menu.clickMenuButton(player, i);
                return menu.getSlot(StonecutterMenu.RESULT_SLOT).getItem();
            }
        }
        helper.fail("the stonecutter offers no " + LOCKED_RECIPE + " for stone");
        return ItemStack.EMPTY;
    }

    @HsGameTest
    public static void aLockedStonecutterRecipeGivesNoResult(GameTestHelper helper) {
        try {
            GameTestStages.individual("stonecutter_locked", stage ->
                    stage.setRecipes(new ArrayList<>(List.of(LOCKED_RECIPE))));
            ServerPlayer player = GameTestPlayers.createConnected(helper);
            if (!cutStoneBricks(helper, player).isEmpty()) {
                helper.fail(LOCKED_RECIPE + " is on a stage this player lacks, but the stonecutter "
                        + "still fills the result slot");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @HsGameTest
    public static void theStonecutterRecipeWorksOnceThePlayerHasTheStage(GameTestHelper helper) {
        IndividualStageData data = IndividualStageData.get(helper.getLevel());
        String stageId = GameTestStages.PREFIX + "stonecutter_unlocked";
        ServerPlayer player = GameTestPlayers.createConnected(helper);
        try {
            GameTestStages.individual("stonecutter_unlocked", stage ->
                    stage.setRecipes(new ArrayList<>(List.of(LOCKED_RECIPE))));
            data.addStage(player.getUUID(), stageId);
            if (!cutStoneBricks(helper, player).is(Items.STONE_BRICKS)) {
                helper.fail("the player has the stage holding " + LOCKED_RECIPE
                        + ", but the stonecutter gives no stone bricks");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            data.removeStage(player.getUUID(), stageId);
        }
    }
}
