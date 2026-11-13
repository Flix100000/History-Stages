package net.bananemdnsa.historystages.init;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.recipe.ResealScrollRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.bananemdnsa.historystages.platform.DeferredHolder;
import net.bananemdnsa.historystages.platform.DeferredRegister;

public class ModRecipes {

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, HistoryStages.MOD_ID);

    /**
     * Registered as a special recipe rather than a shaped one because the result has to carry the
     * stage id read off the input; a data-driven recipe has no way to copy it.
     */
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<ResealScrollRecipe>>
            RESEAL_SCROLL = RECIPE_SERIALIZERS.register("reseal_scroll",
                    () -> new SimpleCraftingRecipeSerializer<>(ResealScrollRecipe::new));

    public static void register() {
        RECIPE_SERIALIZERS.register();
    }
}
