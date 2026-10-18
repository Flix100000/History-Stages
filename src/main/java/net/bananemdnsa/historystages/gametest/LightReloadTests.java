package net.bananemdnsa.historystages.gametest;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.reload.LightReload;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.resource.ContextAwareReloadListener;

/** PROTOTYPE. Holds what the light reload promises: mods hear about it, vanilla does not. */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LightReloadTests {

    private LightReloadTests() {}

    @GameTest(template = "empty")
    public static void theServerReloadListenersAreRemembered(GameTestHelper helper) {
        // The world loading is a datapack reload, so by now the mixin has seen one. An empty list
        // here means the mixin never applied, and every light reload would quietly do nothing.
        boolean sawRecipeManager = LightReload.describe().stream()
                .anyMatch(entry -> entry.kind() == LightReload.Kind.VANILLA
                        && entry.name().equals(RecipeManager.class.getName()));
        if (!sawRecipeManager) {
            helper.fail("no remembered server reload has a recipe manager in it, so the mixin on "
                    + "SimpleReloadInstance.create did not catch the world's own reload");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aLightReloadRunsModListenersAndLeavesTheRecipesAlone(GameTestHelper helper) {
        // Synchronous from end to end, so no other test's reload can replace the list in between.
        MinecraftServer server = helper.getLevel().getServer();
        List<PreparableReloadListener> real = LightReload.remembered();
        RecipeManager recipesBefore = server.getRecipeManager();
        Probe probe = new Probe();
        try {
            List<PreparableReloadListener> fake = new ArrayList<>();
            fake.add(recipesBefore);
            fake.add(probe);
            LightReload.rememberIfServerData(fake);

            LightReload.run(server, false);

            if (probe.calls != 1) {
                helper.fail("the mod listener ran " + probe.calls + " times instead of once, so "
                        + "a mod holding a copy of the recipe list would not be told to let go");
                return;
            }
            if (!probe.hadContext) {
                helper.fail("the mod listener ran without NeoForge's condition context, so any "
                        + "condition it reads would come out false");
                return;
            }
            if (server.getRecipeManager() != recipesBefore) {
                helper.fail("the light reload replaced the recipe manager, which means it did the "
                        + "expensive full reload after all");
                return;
            }
            helper.succeed();
        } finally {
            LightReload.restoreRemembered(real);
        }
    }

    /** A mod's reload listener, reduced to counting. */
    private static final class Probe extends ContextAwareReloadListener implements ResourceManagerReloadListener {
        int calls;
        boolean hadContext;

        @Override
        public void onResourceManagerReload(ResourceManager resourceManager) {
            calls++;
            hadContext = getContext() != ICondition.IContext.EMPTY;
        }
    }
}
