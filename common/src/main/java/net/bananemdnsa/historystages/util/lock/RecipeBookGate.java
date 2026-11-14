package net.bananemdnsa.historystages.util.lock;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.events.RecipeHandler;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Which recipes a player's book shows. 26.2's server sends each player their own book (display
 * entries), so locked recipes are left out there; the book itself (the known ids in the player
 * file) is never touched, an unlock re-sends it whole.
 */
public final class RecipeBookGate {

    private RecipeBookGate() {}

    /** Whether this recipe stays out of the player's book. */
    public static boolean hides(ServerPlayer player, ResourceKey<Recipe<?>> id) {
        if (!Config.VISUAL.hideLockedRecipesInBook.get()) return false;
        MinecraftServer server = player.level().getServer();
        RecipeHolder<?> holder = server.getRecipeManager().byKey(id).orElse(null);
        return RecipeCraftContext.with(player.getUUID(), () ->
                RecipeHandler.isRecipeIdLocked(id.identifier(), false)
                        || (holder != null && RecipeHandler.isOutputLocked(holder, false)));
    }

    /** Sends the player's whole book again (replacing the client's), after their stages changed. */
    public static void resend(ServerPlayer player) {
        if (player.connection == null) return;
        player.getRecipeBook().sendInitialRecipeBook(player);
    }

    public static void resendAll(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            resend(player);
        }
    }
}
