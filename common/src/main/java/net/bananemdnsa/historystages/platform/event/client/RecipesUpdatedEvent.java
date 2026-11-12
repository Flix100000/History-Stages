package net.bananemdnsa.historystages.platform.event.client;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.minecraft.world.item.crafting.RecipeMap;

/**
 * The client has just been sent a new recipe list.
 *
 * <p>Worth knowing about because the client rebuilds everything it had derived from the old one,
 * and anything this mod indexed off recipes has to be built again too.
 *
 * <p>Since 1.21.2 the client no longer holds a full {@code RecipeManager}; the server only sends
 * the recipe types a client needs. What arrives is a {@link RecipeMap}, the same thing NeoForge
 * 26.2 hands out in {@code RecipesReceivedEvent}.
 */
public class RecipesUpdatedEvent extends Event {

    private final RecipeMap recipeMap;

    public RecipesUpdatedEvent(RecipeMap recipeMap) {
        this.recipeMap = recipeMap;
    }

    public RecipeMap getRecipeMap() {
        return recipeMap;
    }
}
