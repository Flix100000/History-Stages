package net.bananemdnsa.historystages.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A recipe gate on the recipe manager FastSuite puts in place of the vanilla one.
 *
 * <p>Written for issue #121: with FastSuite installed a gated recipe could be crafted, and the
 * ingredients stayed in the grid or came back doubled. FastSuite replaces two lookups on a manager
 * subclass of its own, so the gate inside the vanilla bodies never ran for them — while the
 * leftovers question, which it does not replace, still met the gate and heard that no recipe
 * exists. Vanilla consumes nothing when there is no recipe. The hook that closes it is
 * {@code mixin/fastsuite/AuxRecipeManagerMixin}.
 *
 * <p>Asked through the recipe manager rather than through a crafting table, because these run on a
 * headless server where no menu class is ever loaded. What the table does with the answer is
 * vanilla's own, so the answer is the whole of what there is to check — and it is also exactly
 * where the two halves disagreed.
 *
 * <p>Collision partners of a gated recipe are not re-tested here. Both managers now walk the same
 * method to find them, and {@code KubeJsRecipeLockTests} already holds that ground.
 *
 * <p>Each test gates a recipe of its own. The suite ticks its tests side by side, and two of them
 * on the same recipe would each be looking at the other's stage.
 */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FastSuiteRecipeLockTests {

    private static final String FASTSUITE_PACKAGE = "dev.shadowsoffire.fastsuite.";

    private FastSuiteRecipeLockTests() {}

    @GameTest(template = "empty")
    public static void fastSuiteIsTheOneAnswering(GameTestHelper helper) {
        String manager = helper.getLevel().getServer().getRecipeManager().getClass().getName();
        if (!manager.startsWith(FASTSUITE_PACKAGE)) {
            helper.fail("the server's recipe manager is " + manager + ", so FastSuite is not on "
                    + "the runtime classpath and this suite cannot say anything. Its coordinates "
                    + "are in build.gradle next to Create");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aGatedRecipeDoesNotResolve(GameTestHelper helper) {
        gateAndCheck(helper, "fastsuite_resolve", Items.OAK_LOG, "minecraft:oak_planks",
                (server, level, input, gated) -> {
                    if (resolve(server, level, input, null).isPresent()) {
                        return "oak planks are gated and a plain lookup still finds the recipe — "
                                + "the hook on FastSuite's manager did not apply";
                    }
                    // FastSuite hands a still-matching lastRecipe straight back before it consults
                    // its own list at all, and the crafting table passes whatever the grid resolved
                    // to on the previous tick. Ungated, that is the one door a player could hold
                    // open.
                    if (resolve(server, level, input, gated).isPresent()) {
                        return "oak planks are gated and the recipe comes back anyway once it is "
                                + "passed in as the previous one";
                    }
                    return null;
                });
    }

    @GameTest(template = "empty")
    public static void bothLookupsBehindATakeAgree(GameTestHelper helper) {
        // The duplication itself. Taking the result out asks what is left over, which resolves
        // through the three-argument lookup; the result slot was filled through the four-argument
        // one. For as long as those two disagree, vanilla puts the ingredients back in the grid.
        gateAndCheck(helper, "fastsuite_agree", Items.BIRCH_LOG, "minecraft:birch_planks",
                (server, level, input, gated) -> {
                    boolean fillsTheSlot = resolve(server, level, input, null).isPresent();
                    boolean consumes = server.getRecipeManager()
                            .getRecipeFor(RecipeType.CRAFTING, input, level).isPresent();
                    if (fillsTheSlot != consumes) {
                        return "birch planks are gated and the two lookups behind a take disagree: "
                                + "filling the result slot says " + fillsTheSlot + ", consuming the "
                                + "ingredients says " + consumes + ". That gap is the duplication "
                                + "in issue #121";
                    }
                    return null;
                });
    }

    @GameTest(template = "empty")
    public static void aGatedRecipeIsNotInTheListLookupEither(GameTestHelper helper) {
        gateAndCheck(helper, "fastsuite_list", Items.SPRUCE_LOG, "minecraft:spruce_planks",
                (server, level, input, gated) -> {
                    List<RecipeHolder<CraftingRecipe>> resolved = server.getRecipeManager()
                            .getRecipesFor(RecipeType.CRAFTING, input, level);
                    for (RecipeHolder<CraftingRecipe> holder : resolved) {
                        if (holder.id().equals(gated.id())) {
                            return "spruce planks are gated and the list lookup still lists the "
                                    + "recipe";
                        }
                    }
                    return null;
                });
    }

    /** What a test does once its recipe is gated; returns the failure, or null when happy. */
    private interface Check {
        String run(MinecraftServer server, ServerLevel level, CraftingInput input,
                   RecipeHolder<CraftingRecipe> gated);
    }

    /**
     * Puts one recipe on a locked global stage and runs {@code check} against it.
     *
     * <p>The control comes first: a grid that resolves to nothing before anything is gated would
     * let every assertion after it pass for the wrong reason.
     */
    @SuppressWarnings("unchecked")
    private static void gateAndCheck(GameTestHelper helper, String stageName, Item ingredient,
                                     String recipeId, Check check) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        if (!server.getRecipeManager().getClass().getName().startsWith(FASTSUITE_PACKAGE)) {
            helper.fail("needs FastSuite — see fastSuiteIsTheOneAnswering");
            return;
        }

        ResourceLocation id = ResourceLocation.parse(recipeId);
        RecipeHolder<?> holder = server.getRecipeManager().byKey(id).orElse(null);
        if (holder == null) {
            helper.fail(recipeId + " is not loaded, so there is nothing to gate");
            return;
        }
        RecipeHolder<CraftingRecipe> gated = (RecipeHolder<CraftingRecipe>) holder;

        CraftingInput input = CraftingInput.of(1, 1, List.of(new ItemStack(ingredient)));
        if (resolve(server, level, input, null).isEmpty()) {
            helper.fail("a single " + ingredient + " resolves to nothing with everything unlocked "
                    + "— the test's own grid is wrong, not the gate");
            return;
        }

        StageData data = StageData.get(level);
        String stageId = GameTestStages.PREFIX + stageName;
        try {
            GameTestStages.global(stageName, stage ->
                    stage.setRecipes(new ArrayList<>(List.of(recipeId))));

            String failure = check.run(server, level, input, gated);
            if (failure != null) {
                helper.fail(failure);
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            data.removeStage(stageId);
        }
    }

    /** The lookup a crafting table fills its result slot from, with or without a previous recipe. */
    private static Optional<RecipeHolder<CraftingRecipe>> resolve(
            MinecraftServer server, ServerLevel level, CraftingInput input,
            RecipeHolder<CraftingRecipe> lastRecipe) {
        return server.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level, lastRecipe);
    }
}
