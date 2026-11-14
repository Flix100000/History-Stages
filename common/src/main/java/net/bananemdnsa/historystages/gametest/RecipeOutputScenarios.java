package net.bananemdnsa.historystages.gametest;

import net.bananemdnsa.historystages.util.RecipeOutputs;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;

import java.lang.reflect.Proxy;
import java.util.List;

/** Where a recipe's output comes from when output-based locks ask for it. */
public final class RecipeOutputScenarios {

    private RecipeOutputScenarios() {}

    /** A modded recipe's own result method, as many kept it after 1.21.2. */
    public interface LegacyResult {
        ItemStack getResultItem(HolderLookup.Provider registries);
    }

    /** A recipe that shows no display still has its output found through its own result method. */
    public static void aRecipeWithoutDisplayStillHasItsOutput(GameTestHelper helper) {
        Recipe<?> recipe = (Recipe<?>) Proxy.newProxyInstance(RecipeOutputScenarios.class.getClassLoader(),
                new Class<?>[]{Recipe.class, LegacyResult.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "display" -> List.of();
                    case "getResultItem" -> new ItemStack(Items.DIAMOND);
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "display-less test recipe";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        ItemStack result = RecipeOutputs.resultOf(recipe, helper.getLevel().registryAccess());
        if (!result.is(Items.DIAMOND)) {
            helper.fail(Component.literal("a recipe without a display resolved to " + result + ", expected its diamond"));
            return;
        }
        helper.succeed();
    }
}
