package net.bananemdnsa.historystages.client.editor.recipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.bananemdnsa.historystages.client.editor.recipe.IngredientUsage.Kind;
import net.bananemdnsa.historystages.compat.reliableremover.ReliableRemoverCompat;
import net.bananemdnsa.historystages.data.lock.IndividualRecipeSupport;
import net.bananemdnsa.historystages.util.AllRecipesCache;

import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Finds every recipe that takes one item as an ingredient, for the editor's bulk-lock popup.
 *
 * <p>Runs once when the popup opens and hands back plain rows; the popup never looks at a recipe
 * again. Item mode needs more than the recipes using the clicked item: whether a result can be
 * made without locked material depends on <em>all</em> its recipes, so every recipe is read and
 * the answer is kept per result.
 */
public final class IngredientUsageScan {

    /**
     * @param alternatives per input slot, the unlocked items that also fit a dashed slot — what
     *                     the recipe can be made with instead. Empty for every other slot.
     */
    public record RecipeRow(String recipeId, ItemStack result, String typeId, RecipeShape shape,
                            Kind kind, int[] slotMarks, List<List<ItemStack>> alternatives,
                            String searchText) {
    }

    public record ItemRow(String itemId, ItemStack stack, Kind kind, List<RecipeRow> recipes,
                          String searchText) {
    }

    public record Result(List<RecipeRow> recipes, List<ItemRow> items) {
    }

    private IngredientUsageScan() {
    }

    /**
     * @param locked          ids that count as locked; the clicked item is added regardless, since
     *                        the user is asking about it even when its own entry carries NBT
     * @param individualScope true on an individual stage — only recipe types a per-player gate can
     *                        reach are offered, as in the recipe picker
     */
    public static Result scan(String clickedId, Set<String> locked, boolean individualScope) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return new Result(List.of(), List.of());

        Set<String> lockedIds = new HashSet<>(locked);
        lockedIds.add(clickedId);

        RegistryAccess registryAccess = mc.level.registryAccess();
        Collection<RecipeHolder<?>> all = AllRecipesCache.get();
        Collection<RecipeHolder<?>> recipes = all.isEmpty()
                ? mc.level.getRecipeManager().getRecipes()
                : all;

        List<RecipeRow> rows = new ArrayList<>();
        Map<String, List<Boolean>> requiresLockedByResult = new HashMap<>();
        Map<String, List<RecipeRow>> rowsByResult = new LinkedHashMap<>();

        for (RecipeHolder<?> holder : recipes) {
            try {
                Recipe<?> recipe = holder.value();
                ResourceLocation typeKey = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
                String typeId = typeKey == null ? "" : typeKey.toString();
                if (individualScope && !IndividualRecipeSupport.supports(typeId)) continue;

                ItemStack result = recipe.getResultItem(registryAccess);
                if (result.isEmpty()) continue;
                if (ReliableRemoverCompat.isPresent() && ReliableRemoverCompat.isRemoved(result)) continue;
                String resultId = BuiltInRegistries.ITEM.getKey(result.getItem()).toString();

                List<Set<String>> slots = new ArrayList<>();
                for (Ingredient ingredient : recipe.getIngredients()) {
                    slots.add(idsOf(ingredient));
                }
                requiresLockedByResult.computeIfAbsent(resultId, k -> new ArrayList<>())
                        .add(IngredientUsage.requiresLocked(slots, lockedIds));

                Kind kind = IngredientUsage.classify(slots, clickedId, lockedIds);
                if (kind == Kind.NONE) continue;

                RecipeShape shape = RecipeShape.of(recipe);
                String recipeId = holder.id().toString();
                int[] marks = marksFor(shape, clickedId, lockedIds);
                RecipeRow row = new RecipeRow(recipeId, result, typeId, shape, kind, marks,
                        alternativesFor(shape, marks, lockedIds),
                        searchText(result, recipeId, resultId));
                rows.add(row);
                rowsByResult.computeIfAbsent(resultId, k -> new ArrayList<>()).add(row);
            } catch (Exception ignored) {
                // A recipe that throws while being read is one the picker skips too.
            }
        }

        List<ItemRow> items = new ArrayList<>();
        for (Map.Entry<String, List<RecipeRow>> entry : rowsByResult.entrySet()) {
            String resultId = entry.getKey();
            ItemStack stack = entry.getValue().get(0).result().copyWithCount(1);
            Kind kind = IngredientUsage.forResult(
                    requiresLockedByResult.getOrDefault(resultId, List.of()));
            items.add(new ItemRow(resultId, stack, kind, entry.getValue(),
                    searchText(stack, resultId, resultId)));
        }

        Map<String, Integer> registryOrder = registryOrder();
        rows.sort((a, b) -> Integer.compare(order(registryOrder, a.result()),
                order(registryOrder, b.result())));
        items.sort((a, b) -> Integer.compare(order(registryOrder, a.stack()),
                order(registryOrder, b.stack())));
        return new Result(rows, items);
    }

    private static Set<String> idsOf(Ingredient ingredient) {
        Set<String> ids = new HashSet<>();
        for (ItemStack stack : ingredient.getItems()) {
            if (!stack.isEmpty()) ids.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
        return ids;
    }

    private static int[] marksFor(RecipeShape shape, String clickedId, Set<String> lockedIds) {
        List<Ingredient> ingredients = shape.slotIngredients();
        int[] marks = new int[ingredients.size()];
        for (int i = 0; i < ingredients.size(); i++) {
            Kind kind = IngredientUsage.slotKind(idsOf(ingredients.get(i)), clickedId, lockedIds);
            marks[i] = switch (kind) {
                case ONLY_LOCKED -> RecipeCardRenderer.MARK_ONLY_LOCKED;
                case WORKS_WITHOUT -> RecipeCardRenderer.MARK_ALTERNATIVE;
                case NONE -> RecipeCardRenderer.MARK_NONE;
            };
        }
        return marks;
    }

    private static List<List<ItemStack>> alternativesFor(RecipeShape shape, int[] marks,
                                                         Set<String> lockedIds) {
        List<List<ItemStack>> out = new ArrayList<>(marks.length);
        for (int i = 0; i < marks.length; i++) {
            List<ItemStack> unlocked = new ArrayList<>();
            if (marks[i] == RecipeCardRenderer.MARK_ALTERNATIVE) {
                Set<String> seen = new HashSet<>();
                for (ItemStack stack : shape.slotIngredients().get(i).getItems()) {
                    String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    if (!stack.isEmpty() && !lockedIds.contains(id) && seen.add(id)) unlocked.add(stack);
                }
            }
            out.add(unlocked);
        }
        return out;
    }

    /** Name plus "@namespace" for the recipe and the result, so "@create" finds Create's recipes. */
    private static String searchText(ItemStack stack, String recipeId, String resultId) {
        return (stack.getHoverName().getString() + " @" + namespace(recipeId)
                + " @" + namespace(resultId)).toLowerCase();
    }

    private static String namespace(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(0, colon);
    }

    private static Map<String, Integer> registryOrder() {
        Map<String, Integer> order = new HashMap<>();
        int idx = 0;
        for (var item : BuiltInRegistries.ITEM) {
            order.put(BuiltInRegistries.ITEM.getKey(item).toString(), idx++);
        }
        return order;
    }

    private static int order(Map<String, Integer> registryOrder, ItemStack stack) {
        return registryOrder.getOrDefault(
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), Integer.MAX_VALUE);
    }
}
