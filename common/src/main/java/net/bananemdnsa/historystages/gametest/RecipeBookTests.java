package net.bananemdnsa.historystages.gametest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.bananemdnsa.historystages.api.stage.StageStates;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

/**
 * 26.2's server builds each player's recipe book packets, so locked recipes stay out of them
 * (1.21.1 filtered the book on the client). New for the port.
 */
public final class RecipeBookTests {

    private static final String LOCKED_RECIPE = "minecraft:torch";
    private static final ResourceKey<Recipe<?>> KEY =
            ResourceKey.create(Registries.RECIPE, Identifier.parse(LOCKED_RECIPE));

    private RecipeBookTests() {}

    /** Whether the last full book sent shows the locked recipe; null if no full book was sent. */
    private static Boolean lastBookShowsIt(GameTestHelper helper, List<Packet<?>> sent) {
        Set<RecipeDisplayId> torch = new HashSet<>();
        helper.getLevel().getServer().getRecipeManager().listDisplaysForRecipe(KEY, entry -> torch.add(entry.id()));
        for (int i = sent.size() - 1; i >= 0; i--) {
            if (sent.get(i) instanceof ClientboundRecipeBookAddPacket book && book.replace()) {
                return book.entries().stream().anyMatch(entry -> torch.contains(entry.contents().id()));
            }
        }
        return null;
    }

    @HsGameTest
    public static void aLockedRecipeIsLeftOutOfTheBookUntilUnlocked(GameTestHelper helper) {
        IndividualStageData data = IndividualStageData.get(helper.getLevel());
        String stageId = GameTestStages.PREFIX + "book_torch";
        List<Packet<?>> sent = new ArrayList<>();
        ServerPlayer player = GameTestPlayers.createCapturing(helper, sent);
        try {
            GameTestStages.individual("book_torch", stage -> stage.setRecipes(new ArrayList<>(List.of(LOCKED_RECIPE))));
            player.getRecipeBook().add(KEY);
            player.getRecipeBook().sendInitialRecipeBook(player);
            if (!Boolean.FALSE.equals(lastBookShowsIt(helper, sent))) {
                helper.fail(LOCKED_RECIPE + " is on a stage the player lacks but was sent in the recipe book");
                return;
            }
            sent.clear();
            StageStates.unlockIndividual(stageId, player);
            if (!Boolean.TRUE.equals(lastBookShowsIt(helper, sent))) {
                helper.fail("the player unlocked the stage holding " + LOCKED_RECIPE
                        + ", but no book with it was sent");
                return;
            }
            if (!player.getRecipeBook().contains(KEY)) {
                helper.fail("hiding " + LOCKED_RECIPE + " took it out of the player's saved book");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            data.removeStage(player.getUUID(), stageId);
        }
    }
}
