package net.bananemdnsa.historystages.gametest;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A recipe a script wrote, at a machine that is not vanilla.
 *
 * <p>Written for a report against 5.6.1-1.20.1: a mechanical crafting recipe added through KubeJS
 * is found by the editor's picker and can be put on a stage, but the crafter keeps making it. The
 * recipes come from {@code run-gametest/kubejs/server_scripts/hstg_create_repro.js}.
 *
 * <p>Everything here goes through {@code getRecipeFor}, because that is the one question Create's
 * mechanical crafter asks — its whole package touches the recipe manager nowhere else, so a recipe
 * that survives this call is a recipe the crafter will make.
 *
 * <p>Each test has a recipe of its own. The suite ticks its tests side by side, and two of these
 * gating the same recipe would each see the other's stage.
 */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KubeJsRecipeLockTests {

    private static final ResourceLocation MECHANICAL_CRAFTING =
            ResourceLocation.parse("create:mechanical_crafting");

    /** Gated by its id: 2x2 dirt to a prismarine shard. */
    private static final ResourceLocation BY_ID =
            ResourceLocation.parse("kubejs:hstg_repro_mechanical");

    /** Gated by nothing, ever — the control. 2x2 sand to a heart of the sea. */
    private static final ResourceLocation CONTROL =
            ResourceLocation.parse("kubejs:hstg_repro_control");

    /** A plain shaped recipe, reachable in the crafter without being a mechanical one. */
    private static final ResourceLocation SHAPED =
            ResourceLocation.parse("kubejs:hstg_repro_shaped");

    /** Shares its ingredients with vanilla red sandstone, deliberately. */
    private static final ResourceLocation COLLIDING =
            ResourceLocation.parse("kubejs:hstg_repro_collision");

    private KubeJsRecipeLockTests() {}

    @GameTest(template = "empty")
    public static void theScriptedRecipesResolveAtAll(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();

        if (mechanicalCrafting() == null) {
            helper.fail("create:mechanical_crafting is not a registered recipe type — Create is "
                    + "not on the runtime classpath, so this suite cannot say anything");
            return;
        }
        if (server.getRecipeManager().byKey(CONTROL).isEmpty()) {
            helper.fail(CONTROL + " is not loaded. Either the script did not run or its json was "
                    + "rejected — the KubeJS console in run-gametest/logs says which");
            return;
        }
        // The auto-named one is looked up by what it makes, because nobody knows what KubeJS
        // called it. On 1.21 it comes out as create:kjs/<hash>, not kubejs:<something>.
        if (autoNamed(level) == null) {
            helper.fail("the script's recipe without an id of its own is not loaded");
            return;
        }
        if (crafterGrid(Items.SAND) == null) {
            helper.fail("Create's MechanicalCraftingInput could not be built — its shape changed, "
                    + "and every test here that says \"does not resolve\" would be lying");
            return;
        }
        if (!resolves(server, level, Items.SAND)) {
            helper.fail("2x2 sand does not resolve to " + CONTROL + ", and nothing gates it. The "
                    + "test's own input is wrong, not the gate");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void gatingTheIdStopsTheCrafterResolvingIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        if (!loaded(helper, server, BY_ID)) return;

        StageData data = StageData.get(level);
        String stageId = GameTestStages.PREFIX + "kubejs_recipe_id";
        try {
            GameTestStages.global("kubejs_recipe_id", stage ->
                    stage.setRecipes(new ArrayList<>(List.of(BY_ID.toString()))));

            if (resolves(server, level, Items.DIRT)) {
                helper.fail(BY_ID + " sits on a locked global stage and still resolves. A "
                        + "mechanical crafter asks exactly this question, so it would keep making "
                        + "it — this is the reported bug");
                return;
            }

            data.addStage(stageId);
            StageData.refreshCache(data.getUnlockedStages());

            if (!resolves(server, level, Items.DIRT)) {
                helper.fail("the stage is unlocked and " + BY_ID + " still does not resolve — the "
                        + "gate is answering from a cache it never dropped");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            data.removeStage(stageId);
        }
    }

    @GameTest(template = "empty")
    public static void gatingTheOutputItemStopsTheCrafterResolvingIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        RecipeHolder<?> scripted = autoNamed(level);
        if (mechanicalCrafting() == null || scripted == null) {
            helper.fail("needs Create and the scripted recipes — see theScriptedRecipesResolveAtAll");
            return;
        }

        StageData data = StageData.get(level);
        String stageId = GameTestStages.PREFIX + "kubejs_recipe_output";
        try {
            GameTestStages.global("kubejs_recipe_output", stage ->
                    stage.setItemEntries(new ArrayList<>(
                            List.of(new ItemEntry("minecraft:nautilus_shell")))));

            if (resolves(server, level, Items.COBBLESTONE)) {
                helper.fail("minecraft:nautilus_shell sits in a locked global stage, so " + scripted.id()
                        + " should not resolve any more. The item route is blind to this recipe");
                return;
            }

            data.addStage(stageId);
            StageData.refreshCache(data.getUnlockedStages());

            if (!resolves(server, level, Items.COBBLESTONE)) {
                helper.fail("the stage holding the output item is unlocked and " + scripted.id()
                        + " still does not resolve");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            data.removeStage(stageId);
        }
    }

    @GameTest(template = "empty")
    public static void gatingAShapedRecipeStopsTheCrafterResolvingIt(GameTestHelper helper) {
        // What the reporter actually hit: Create lets ordinary crafting happen in the crafter as
        // well, so a plain shaped recipe is reachable there without ever being a mechanical
        // crafting recipe. Same gate, but a different recipe type goes into it.
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        if (!loaded(helper, server, SHAPED)) return;

        StageData data = StageData.get(level);
        String stageId = GameTestStages.PREFIX + "kubejs_shaped_id";
        try {
            if (!resolvesAs(server, level, RecipeType.CRAFTING, Items.GRAVEL)) {
                helper.fail(SHAPED + " does not resolve in a crafter grid even with nothing "
                        + "locked — the test's own input is wrong, not the gate");
                return;
            }

            GameTestStages.global("kubejs_shaped_id", stage ->
                    stage.setRecipes(new ArrayList<>(List.of(SHAPED.toString()))));

            if (resolvesAs(server, level, RecipeType.CRAFTING, Items.GRAVEL)) {
                helper.fail(SHAPED + " sits on a locked global stage and a mechanical crafter "
                        + "would still make it — this is the reported bug");
                return;
            }

            data.addStage(stageId);
            StageData.refreshCache(data.getUnlockedStages());

            if (!resolvesAs(server, level, RecipeType.CRAFTING, Items.GRAVEL)) {
                helper.fail("the stage is unlocked and " + SHAPED + " still does not resolve");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            data.removeStage(stageId);
        }
    }

    @GameTest(template = "empty")
    public static void gatingARecipeLeavesItsCollisionPartnerAlone(GameTestHelper helper) {
        // Vanilla answers with the first recipe that matches and stops there, so a gate that
        // simply empties the answer removes every other recipe on the same ingredients as well.
        // COLLIDING sits on 2x2 red sand, which is vanilla red sandstone.
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        if (server.getRecipeManager().byKey(COLLIDING).isEmpty()) {
            helper.fail("needs " + COLLIDING + " — see theScriptedRecipesResolveAtAll");
            return;
        }

        StageData data = StageData.get(level);
        String stageId = GameTestStages.PREFIX + "kubejs_collision";
        try {
            if (!resolvesAs(server, level, RecipeType.CRAFTING, Items.RED_SAND)) {
                helper.fail("2x2 red sand resolves to nothing with everything unlocked — the "
                        + "test's own input is wrong, not the gate");
                return;
            }

            GameTestStages.global("kubejs_collision", stage ->
                    stage.setRecipes(new ArrayList<>(List.of(COLLIDING.toString()))));

            ItemStack still = resolvedResult(server, level, Items.RED_SAND);
            if (still.isEmpty()) {
                helper.fail(COLLIDING + " is locked and 2x2 red sand now makes nothing at all — "
                        + "the gate took vanilla red sandstone down with it");
                return;
            }
            if (!still.is(Items.RED_SANDSTONE)) {
                helper.fail("2x2 red sand makes " + still + " while " + COLLIDING + " is locked; "
                        + "vanilla red sandstone was expected");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            data.removeStage(stageId);
        }
    }

    /** What a crafter grid of this item actually produces, empty when nothing resolves. */
    private static ItemStack resolvedResult(MinecraftServer server, ServerLevel level, Item filling) {
        return server.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, crafterGrid(filling), level)
                .map(holder -> holder.value().getResultItem(level.registryAccess()))
                .orElse(ItemStack.EMPTY);
    }

    private static boolean loaded(GameTestHelper helper, MinecraftServer server, ResourceLocation id) {
        if (mechanicalCrafting() == null || server.getRecipeManager().byKey(id).isEmpty()) {
            helper.fail("needs Create and " + id + " — see theScriptedRecipesResolveAtAll");
            return false;
        }
        return true;
    }

    /**
     * Whether the recipe manager still answers the question a mechanical crafter asks, for a 2x2
     * grid of one item. Empty means the gate took it.
     */
    private static boolean resolves(MinecraftServer server, ServerLevel level, Item filling) {
        return resolvesAs(server, level, mechanicalCrafting(), filling);
    }

    /** The same question for a given recipe type — the crafter asks two, one per branch. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean resolvesAs(MinecraftServer server, ServerLevel level,
                                      RecipeType<?> type, Item filling) {
        return server.getRecipeManager()
                .getRecipeFor((RecipeType) type, crafterGrid(filling), level)
                .isPresent();
    }

    /**
     * The grid a mechanical crafter hands over, built by reflection.
     *
     * <p>A plain {@link CraftingInput} is not enough: Create's recipe opens with
     * {@code if (!(input instanceof MechanicalCraftingInput)) return false}, so anything else
     * silently never matches and a test built on one proves nothing. The class has no public way
     * in — its only factory takes Create's own grouped-items type — and Create is a runtime-only
     * dependency here, so there is nothing to compile against either.
     */
    private static CraftingInput crafterGrid(Item filling) {
        ItemStack stack = new ItemStack(filling);
        List<ItemStack> items = List.of(stack, stack.copy(), stack.copy(), stack.copy());
        try {
            Class<?> type = Class.forName(
                    "com.simibubi.create.content.kinetics.crafter.MechanicalCraftingInput");
            Constructor<?> ctor = type.getDeclaredConstructor(int.class, int.class, List.class);
            ctor.setAccessible(true);
            return (CraftingInput) ctor.newInstance(2, 2, items);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return null;
        }
    }

    private static RecipeType<?> mechanicalCrafting() {
        return BuiltInRegistries.RECIPE_TYPE.get(MECHANICAL_CRAFTING);
    }

    /** The script's recipe without an id of its own, found by what it makes. */
    private static RecipeHolder<?> autoNamed(ServerLevel level) {
        RecipeType<?> type = mechanicalCrafting();
        for (RecipeHolder<?> holder : level.getServer().getRecipeManager().getOrderedRecipes()) {
            if (holder.value().getType() != type) continue;
            if (holder.value().getResultItem(level.registryAccess()).is(Items.NAUTILUS_SHELL)) {
                return holder;
            }
        }
        return null;
    }
}
