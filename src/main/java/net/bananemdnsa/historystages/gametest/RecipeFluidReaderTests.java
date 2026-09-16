package net.bananemdnsa.historystages.gametest;

import java.util.List;
import java.util.Map;
import java.util.Set;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.lock.FluidRecipeScanner;
import net.bananemdnsa.historystages.data.lock.RecipeFluidReader;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The fluid recipe index on this branch reads recipe objects rather than recipe JSON, so what it
 * finds depends on how mods lay their recipes out. These recipes are laid out the way Create does
 * it — item ingredients beside a list of fluid ingredients that only answer through an accessor,
 * and a list of fluid stacks as results — which is the shape the reader has to get right.
 */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecipeFluidReaderTests {

    private RecipeFluidReaderTests() {}

    @GameTest(template = "empty")
    public static void aModdedFluidRecipeIsReadOnTheRightSides(GameTestHelper helper) {
        Map<String, Set<FluidRecipeScanner.Position>> found = scan(new MixingLikeRecipe());

        if (!found.getOrDefault("minecraft:water", Set.of()).contains(FluidRecipeScanner.Position.INPUT)) {
            helper.fail("the fluid ingredient was not read as an input: " + found);
            return;
        }
        if (!found.getOrDefault("minecraft:lava", Set.of()).contains(FluidRecipeScanner.Position.OUTPUT)) {
            helper.fail("the fluid result was not read as an output: " + found);
            return;
        }
        if (found.getOrDefault("minecraft:water", Set.of()).contains(FluidRecipeScanner.Position.OUTPUT)) {
            helper.fail("the ingredient was also counted as an output: " + found);
            return;
        }
        helper.succeed();
    }

    /** The control: a recipe with only item data must mention no fluid at all. */
    @GameTest(template = "empty")
    public static void anItemOnlyRecipeMentionsNoFluid(GameTestHelper helper) {
        Map<String, Set<FluidRecipeScanner.Position>> found = scan(new ItemOnlyRecipe());
        if (!found.isEmpty()) {
            helper.fail("an item-only recipe was read as mentioning " + found);
            return;
        }
        helper.succeed();
    }

    private static Map<String, Set<FluidRecipeScanner.Position>> scan(Recipe<?> recipe) {
        return FluidRecipeScanner.scan(RecipeFluidReader.read(recipe),
                id -> {
                    ResourceLocation key = ResourceLocation.tryParse(id);
                    return key != null && BuiltInRegistries.FLUID.containsKey(key);
                },
                id -> {
                    ResourceLocation key = ResourceLocation.tryParse(id);
                    return key != null && BuiltInRegistries.ITEM.containsKey(key);
                });
    }

    /** Stands in for a fluid ingredient class that only exposes its fluids through a method. */
    public static final class FakeFluidIngredient {
        public List<FluidStack> getMatchingFluidStacks() {
            return List.of(new FluidStack(Fluids.WATER, 250));
        }
    }

    private abstract static class TestRecipe implements Recipe<Container> {
        @Override public boolean matches(Container container, Level level) { return false; }
        @Override public ItemStack assemble(Container container, RegistryAccess access) { return ItemStack.EMPTY; }
        @Override public boolean canCraftInDimensions(int width, int height) { return true; }
        @Override public ItemStack getResultItem(RegistryAccess access) { return ItemStack.EMPTY; }
        @Override public ResourceLocation getId() { return new ResourceLocation(HistoryStages.MOD_ID, "gametest_fluid_reader"); }
        @Override public RecipeSerializer<?> getSerializer() { return null; }
        @Override public RecipeType<?> getType() { return RecipeType.CRAFTING; }
    }

    private static final class MixingLikeRecipe extends TestRecipe {
        final NonNullList<Ingredient> ingredients = NonNullList.of(Ingredient.EMPTY, Ingredient.EMPTY);
        final NonNullList<FakeFluidIngredient> fluidIngredients = NonNullList.of(null, new FakeFluidIngredient());
        final NonNullList<FluidStack> fluidResults = NonNullList.of(FluidStack.EMPTY, new FluidStack(Fluids.LAVA, 100));
    }

    private static final class ItemOnlyRecipe extends TestRecipe {
        final NonNullList<Ingredient> ingredients = NonNullList.of(Ingredient.EMPTY, Ingredient.EMPTY);
        final ItemStack result = ItemStack.EMPTY;
    }
}
