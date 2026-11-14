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

    /**
     * Puts stone in a fresh stonecutter for this player and clicks the locked recipe's button.
     * {@code lockAfterButtons} runs once the buttons exist, before the click.
     */
    private static ItemStack cutStoneBricks(GameTestHelper helper, ServerPlayer player, Runnable lockAfterButtons) {
        StonecutterMenu menu = new StonecutterMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
        menu.getSlot(StonecutterMenu.INPUT_SLOT).set(new ItemStack(Items.STONE));
        lockAfterButtons.run();
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

    /**
     * The backstop: a button the player already has (a list sent before the lock) gives no result
     * once the recipe is locked.
     */
    @HsGameTest
    public static void aLockedStonecutterRecipeGivesNoResult(GameTestHelper helper) {
        try {
            ServerPlayer player = GameTestPlayers.createConnected(helper);
            ItemStack result = cutStoneBricks(helper, player, () -> GameTestStages.individual("stonecutter_locked",
                    stage -> stage.setRecipes(new ArrayList<>(List.of(LOCKED_RECIPE)))));
            if (!result.isEmpty()) {
                helper.fail(LOCKED_RECIPE + " is on a stage this player lacks, but the stonecutter "
                        + "still fills the result slot");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /**
     * The locked recipe is neither a button on the server nor in the list the player is sent, and
     * the buttons the client would build from that list are the server's, in the same order.
     */
    @HsGameTest
    public static void aLockedStonecutterRecipeIsNoButtonOnEitherSide(GameTestHelper helper) {
        try {
            GameTestStages.individual("stonecutter_buttons", stage ->
                    stage.setRecipes(new ArrayList<>(List.of(LOCKED_RECIPE))));
            List<net.minecraft.network.protocol.Packet<?>> sent = new ArrayList<>();
            ServerPlayer player = GameTestPlayers.createCapturing(helper, sent);

            StonecutterMenu menu = new StonecutterMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
            menu.getSlot(StonecutterMenu.INPUT_SLOT).set(new ItemStack(Items.STONE));
            List<String> serverButtons = new ArrayList<>();
            for (var entry : menu.getVisibleRecipes().entries()) {
                serverButtons.add(entry.recipe().recipe().map(h -> h.id().identifier().toString()).orElse("?"));
            }
            if (serverButtons.contains(LOCKED_RECIPE)) {
                helper.fail(LOCKED_RECIPE + " is locked for this player but is a stonecutter button");
                return;
            }

            net.bananemdnsa.historystages.util.lock.RecipeBookGate.resend(player);
            net.bananemdnsa.historystages.util.lock.RecipeBookGate.flush(helper.getLevel().getServer());
            net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket packet = null;
            for (var p : sent) {
                if (p instanceof net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket r) packet = r;
            }
            if (packet == null) {
                helper.fail("no recipe packet was sent to the player");
                return;
            }
            // The client keeps only the displays; compare what it would show for stone, in order.
            var fromPacket = packet.stonecutterRecipes().selectByInput(new ItemStack(Items.STONE)).entries();
            var fromMenu = menu.getVisibleRecipes().entries();
            if (fromPacket.size() != fromMenu.size()) {
                helper.fail("the player is sent " + fromPacket.size() + " stonecutter buttons for stone, the "
                        + "server has " + fromMenu.size() + " - clicks would select the wrong recipe");
                return;
            }
            for (int i = 0; i < fromMenu.size(); i++) {
                if (!fromPacket.get(i).recipe().optionDisplay().equals(fromMenu.get(i).recipe().optionDisplay())) {
                    helper.fail("stonecutter button " + i + " differs between the player's list and the server's");
                    return;
                }
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
            if (!cutStoneBricks(helper, player, () -> {}).is(Items.STONE_BRICKS)) {
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
