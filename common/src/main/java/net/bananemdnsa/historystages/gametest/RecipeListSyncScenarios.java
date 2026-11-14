package net.bananemdnsa.historystages.gametest;

import net.bananemdnsa.historystages.network.RecipeListSync;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/** The editor's recipe list on a dedicated server: what is written reads back, and only for operators. */
public final class RecipeListSyncScenarios {

    private RecipeListSyncScenarios() {}

    public static void theRecipeListReadsBackWhole(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Collection<RecipeHolder<?>> recipes = server.getRecipeManager().getRecipes();
        int[] skipped = new int[1];
        List<RecipeHolder<?>> back = RecipeListSync.fromJson(RecipeListSync.toJson(server, recipes),
                server.registryAccess(), skipped);
        if (recipes.isEmpty() || back.size() != recipes.size() || skipped[0] != 0) {
            helper.fail(Component.literal(recipes.size() + " recipes written, " + back.size() + " read back, "
                    + skipped[0] + " skipped"));
            return;
        }
        helper.succeed();
    }

    /** A player without operator rights does not get the list and is not sent it after reloads. */
    public static void onlyEditorsGetTheList(GameTestHelper helper, Function<GameTestHelper, ServerPlayer> players) {
        ServerPlayer player = players.apply(helper);
        RecipeListSync.sendTo(player);
        if (RecipeListSync.isTracked(player.getUUID())) {
            helper.fail(Component.literal("a player without editor rights was sent the recipe list"));
            return;
        }
        helper.succeed();
    }
}
