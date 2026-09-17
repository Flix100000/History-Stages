package net.bananemdnsa.historystages.gametest;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

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
 * <p>On 1.20.1 FastSuite overrides the three-argument lookup and the list lookup, and leaves the
 * cached one a furnace uses alone — so that one still meets the gate on the vanilla manager and is
 * not worth a test here.
 *
 * <p>Asked through the recipe manager rather than through a crafting table, because these run on a
 * headless server where no menu class is ever loaded. What the table does with the answer is
 * vanilla's own, so the answer is the whole of what there is to check — and it is also exactly
 * where the two halves disagreed.
 *
 * <p>Collision partners of a gated recipe are not re-tested here. Both managers now walk the same
 * method to find them, and {@code RecipeLockTests} already holds that ground.
 *
 * <p>Each test gates a recipe of its own. The suite ticks its tests side by side, and two of them
 * on the same recipe would each be looking at the other's stage.
 *
 * <p>FastSuite is not in the dev runtime, so these are optional: without it they fail with a
 * message saying so and the rest of the suite still passes. To run them for real, add
 * {@code runtimeOnly fg.deobf("maven.modrinth:fastsuite:nhk4VpGm")} and its dependency
 * {@code runtimeOnly fg.deobf("maven.modrinth:placebo:8.6.3")} to build.gradle.
 */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FastSuiteRecipeLockTests {

    private static final String FASTSUITE_PACKAGE = "dev.shadowsoffire.fastsuite.";

    private FastSuiteRecipeLockTests() {}

    @GameTest(template = "empty", required = false)
    public static void fastSuiteIsTheOneAnswering(GameTestHelper helper) {
        String manager = helper.getLevel().getServer().getRecipeManager().getClass().getName();
        if (!manager.startsWith(FASTSUITE_PACKAGE)) {
            helper.fail("the server's recipe manager is " + manager + ", so FastSuite is not on "
                    + "the runtime classpath and this suite cannot say anything. The class comment "
                    + "has the two build.gradle lines that bring it in");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", required = false)
    public static void aGatedRecipeDoesNotResolve(GameTestHelper helper) {
        gateAndCheck(helper, "fastsuite_resolve", Items.OAK_LOG, "minecraft:oak_planks",
                (server, level, grid, gated) -> {
                    if (server.getRecipeManager()
                            .getRecipeFor(RecipeType.CRAFTING, grid, level).isPresent()) {
                        return "oak planks are gated and the lookup a crafting table fills its "
                                + "result slot from still finds the recipe — the hook on "
                                + "FastSuite's manager did not apply";
                    }
                    return null;
                });
    }

    @GameTest(template = "empty", required = false)
    public static void aGatedRecipeIsNotInTheListLookupEither(GameTestHelper helper) {
        gateAndCheck(helper, "fastsuite_list", Items.SPRUCE_LOG, "minecraft:spruce_planks",
                (server, level, grid, gated) -> {
                    List<CraftingRecipe> resolved = server.getRecipeManager()
                            .getRecipesFor(RecipeType.CRAFTING, grid, level);
                    for (CraftingRecipe recipe : resolved) {
                        if (recipe.getId().equals(gated.getId())) {
                            return "spruce planks are gated and the list lookup still lists the "
                                    + "recipe";
                        }
                    }
                    return null;
                });
    }

    /** What a test does once its recipe is gated; returns the failure, or null when happy. */
    private interface Check {
        String run(MinecraftServer server, ServerLevel level, CraftingContainer grid,
                   CraftingRecipe gated);
    }

    /**
     * Puts one recipe on a locked global stage and runs {@code check} against it.
     *
     * <p>The control comes first: a grid that resolves to nothing before anything is gated would
     * let every assertion after it pass for the wrong reason.
     */
    private static void gateAndCheck(GameTestHelper helper, String stageName, Item ingredient,
                                     String recipeId, Check check) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        if (!server.getRecipeManager().getClass().getName().startsWith(FASTSUITE_PACKAGE)) {
            helper.fail("needs FastSuite — see fastSuiteIsTheOneAnswering");
            return;
        }

        Recipe<?> recipe = server.getRecipeManager()
                .byKey(new ResourceLocation(recipeId)).orElse(null);
        if (!(recipe instanceof CraftingRecipe gated)) {
            helper.fail(recipeId + " is not loaded, so there is nothing to gate");
            return;
        }

        CraftingContainer grid = oneSlotGrid(ingredient);
        if (server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level).isEmpty()) {
            helper.fail("a single " + ingredient + " resolves to nothing with everything unlocked "
                    + "— the test's own grid is wrong, not the gate");
            return;
        }

        StageData data = StageData.get(level);
        String stageId = GameTestStages.PREFIX + stageName;
        try {
            GameTestStages.global(stageName, stage ->
                    stage.setRecipes(new ArrayList<>(List.of(recipeId))));

            String failure = check.run(server, level, grid, gated);
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

    /**
     * A one-by-one crafting grid holding that item.
     *
     * <p>The container wants a menu and nothing here has one, so it gets an empty stand-in. A
     * recipe only ever reads the grid's size and contents; the menu is for the change callback,
     * which never fires because nothing is moved.
     */
    private static CraftingContainer oneSlotGrid(Item ingredient) {
        AbstractContainerMenu noMenu = new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(Player player, int index) {
                return ItemStack.EMPTY;
            }

            @Override public boolean stillValid(Player player) {
                return true;
            }
        };
        CraftingContainer grid = new TransientCraftingContainer(noMenu, 1, 1);
        grid.setItem(0, new ItemStack(ingredient));
        return grid;
    }
}
