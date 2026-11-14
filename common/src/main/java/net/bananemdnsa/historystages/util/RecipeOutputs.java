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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;

/**
 * What 1.21.1's {@code Recipe.getResultItem(registries)} answered. 26.2 dropped that method; the
 * output now lives in the recipe's displays, so the first display's result stands in for it.
 *
 * <p>Every vanilla recipe with a fixed output describes itself (shaped, shapeless, cooking,
 * stonecutting, smithing, transmute); the special crafting recipes had no fixed output on 1.21.1
 * either. A modded recipe that ships no display falls back to its own result method if it kept
 * one under the old names. PORT(26.2-gap): a modded recipe with neither has no output as far as
 * output-based locks go; listed in PROGRESS.
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
        if (!displays.isEmpty()) {
            ItemStack shown = displays.getFirst().result().resolveForFirstStack(context(registries));
            if (!shown.isEmpty()) return shown;
        }
        return legacyResult(recipe, registries);
    }

    private static ContextMap context(@Nullable HolderLookup.Provider registries) {
        ContextMap.Builder context = new ContextMap.Builder();
        if (registries != null) context.withParameter(SlotDisplayContext.REGISTRIES, registries);
        return context.create(SlotDisplayContext.CONTEXT);
    }

    private static final java.util.Map<Class<?>, Optional<java.lang.reflect.Method>> LEGACY_RESULT =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** A modded recipe's own result method, as many kept it after 1.21.2: getResultItem(registries) first. */
    private static ItemStack legacyResult(Recipe<?> recipe, @Nullable HolderLookup.Provider registries) {
        Optional<java.lang.reflect.Method> method = LEGACY_RESULT.computeIfAbsent(recipe.getClass(), RecipeOutputs::findLegacyResult);
        if (method.isEmpty()) return ItemStack.EMPTY;
        try {
            java.lang.reflect.Method m = method.get();
            Object result = m.getParameterCount() == 0 ? m.invoke(recipe) : m.invoke(recipe, registries);
            return result instanceof ItemStack stack ? stack.copy() : ItemStack.EMPTY;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    private static Optional<java.lang.reflect.Method> findLegacyResult(Class<?> type) {
        for (String name : List.of("getResultItem", "getResult", "getOutput", "result", "output")) {
            for (java.lang.reflect.Method m : type.getMethods()) {
                if (!m.getName().equals(name) || m.getReturnType() != ItemStack.class) continue;
                Class<?>[] params = m.getParameterTypes();
                if (params.length == 0 || (params.length == 1 && params[0] == HolderLookup.Provider.class)) {
                    return Optional.of(m);
                }
            }
        }
        return Optional.empty();
    }

    /** 1.21.1 recipe ids were plain identifiers; in 26.2 the holder carries a registry key. */
    public static Identifier idOf(RecipeHolder<?> holder) {
        return holder.id().identifier();
    }

    public static Identifier idOf(ResourceKey<Recipe<?>> key) {
        return key.identifier();
    }

    /**
     * 1.21.1's {@code Recipe.getIngredients()}. A shaped recipe keeps its grid with null for an
     * empty cell (26.2 has no empty ingredient); anything else lists what it places.
     */
    public static List<Ingredient> ingredientsOf(Recipe<?> recipe) {
        if (recipe instanceof ShapedRecipe shaped) {
            List<Ingredient> out = new ArrayList<>();
            for (Optional<Ingredient> cell : shaped.getIngredients()) out.add(cell.orElse(null));
            return out;
        }
        try {
            return recipe.placementInfo().ingredients();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /**
     * 1.21.1's {@code Ingredient.getItems()}; null (an empty grid cell) has none. Through the
     * ingredient's display where it has stacks, which keeps the components a loader's custom
     * ingredient asks for (a specific potion, an enchanted book); the bare items otherwise.
     */
    public static ItemStack[] itemsOf(@Nullable Ingredient ingredient) {
        if (ingredient == null) return new ItemStack[0];
        try {
            List<ItemStack> shown = ingredient.display().resolveForStacks(context(CurrentRegistries.get()));
            if (!shown.isEmpty()) return shown.toArray(ItemStack[]::new);
        } catch (RuntimeException e) {
            // A display that cannot resolve without a level; the items below still answer.
        }
        return ingredient.items().map(ItemStack::new).toArray(ItemStack[]::new);
    }
}
