package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.util.AllRecipesCache;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.Collection;
import java.util.List;

/**
 * The recipe list the editor reads. Since 1.21.2 a client gets no recipes from the server, so
 * this falls back to the integrated server's list; on a dedicated server it is the cache or empty.
 */
public final class ClientRecipes {

    private ClientRecipes() {}

    public static Collection<RecipeHolder<?>> all() {
        Collection<RecipeHolder<?>> cached = AllRecipesCache.get();
        if (!cached.isEmpty()) return cached;
        // PORT(26.2-gap): a dedicated server sends no recipe list; the editor's recipe pickers
        // stay empty there until a sync packet exists.
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        return server != null ? server.getRecipeManager().getRecipes() : List.of();
    }
}
