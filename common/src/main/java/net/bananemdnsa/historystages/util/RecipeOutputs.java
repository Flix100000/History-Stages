package net.bananemdnsa.historystages.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What 1.21.1's {@code Recipe.getResultItem(registries)} answered. 26.2 dropped that method; the
 * output now lives in the recipe's displays, so the first display's result stands in for it.
 */
public final class RecipeOutputs {

    private RecipeOutputs() {}

    /** The recipe's output, or {@link ItemStack#EMPTY} for a recipe without a fixed one. */
    public static ItemStack resultOf(Recipe<?> recipe, @Nullable HolderLookup.Provider registries) {
        List<RecipeDisplay> displays;
        try {
            displays = recipe.display();
        } catch (RuntimeException e) {
            // A modded recipe that cannot describe itself has no output as far as the locks go.
            return ItemStack.EMPTY;
        }
        if (displays.isEmpty()) return ItemStack.EMPTY;
        ContextMap.Builder context = new ContextMap.Builder();
        if (registries != null) context.withParameter(SlotDisplayContext.REGISTRIES, registries);
        return displays.getFirst().result().resolveForFirstStack(context.create(SlotDisplayContext.CONTEXT));
    }

    /** 1.21.1 recipe ids were plain identifiers; in 26.2 the holder carries a registry key. */
    public static Identifier idOf(RecipeHolder<?> holder) {
        return holder.id().identifier();
    }

    public static Identifier idOf(ResourceKey<Recipe<?>> key) {
        return key.identifier();
    }
}
