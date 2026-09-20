package net.bananemdnsa.historystages.platform.event.client;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * The client has just been sent a new recipe list.
 *
 * <p>Worth knowing about because the client rebuilds everything it had derived from the old one,
 * and anything this mod indexed off recipes has to be built again too.
 */
@Environment(EnvType.CLIENT)
public class RecipesUpdatedEvent extends Event {

    private final RecipeManager recipeManager;

    public RecipesUpdatedEvent(RecipeManager recipeManager) {
        this.recipeManager = recipeManager;
    }

    public RecipeManager getRecipeManager() {
        return recipeManager;
    }
}
