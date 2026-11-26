package net.bananemdnsa.historystages.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.reload.LightReload;
import net.bananemdnsa.historystages.reload.StageReloadMode;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.crafting.RecipeManager;

/** PROTOTYPE. Holds what the light reload promises: mods hear about it, vanilla does not. */
public class LightReloadTests {

    public LightReloadTests() {}

    @HsGameTest
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

    @HsGameTest
    public static void theTagsEventGetsTheLootTablesToo(GameTestHelper helper) {
        // 26.2's server registryAccess() has no loot tables; mods reading them on the replayed
        // tags event (Jade does) threw until the event got the reload's full view.
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(net.bananemdnsa.historystages.reload.FullRegistriesHolder.of(server)
                        .lookup(net.minecraft.core.registries.Registries.LOOT_TABLE).isPresent(),
                "the registries handed to the tags event have no loot tables");
        helper.succeed();
    }

    @HsGameTest(batch = "targeted_reload", timeoutTicks = 8000)
    public static void aLightReloadRunsModListenersAndLeavesTheRecipesAlone(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        List<PreparableReloadListener> real = LightReload.remembered();
        RecipeManager recipesBefore = server.getRecipeManager();
        Probe probe = new Probe();

        List<PreparableReloadListener> fake = new ArrayList<>();
        fake.add(recipesBefore);
        fake.add(probe);
        LightReload.rememberIfServerData(fake);
        CompletableFuture<LightReload.Result> run;
        try {
            run = LightReload.start(server, false);
        } finally {
            // The list is read when the run starts; put the real one back before anything else
            // gets to reload with the fake.
            LightReload.restoreRemembered(real);
        }

        helper.succeedWhen(() -> {
            helper.assertTrue(run.isDone(), "the light reload is still running");
            helper.assertFalse(run.isCompletedExceptionally(), "the light reload failed");
            helper.assertTrue(probe.calls == 1, "the mod listener ran " + probe.calls + " times instead "
                    + "of once, so a mod holding a copy of the recipe list would not be told to let go");
            helper.assertTrue(server.getRecipeManager() == recipesBefore, "the light reload replaced the "
                    + "recipe manager, which means it did the expensive full reload after all");
        });
    }

    @HsGameTest(batch = "targeted_reload_skip", timeoutTicks = 8000)
    public static void aListenerNamedInTheConfigIsLeftOutAndTheRestStillRuns(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        List<PreparableReloadListener> real = LightReload.remembered();
        List<? extends String> skipBefore = Config.GAMEPLAY.targetedReloadSkip.get();
        Probe skippedProbe = new Probe();
        OtherProbe keptProbe = new OtherProbe();

        LightReload.rememberIfServerData(List.of(server.getRecipeManager(), skippedProbe, keptProbe));
        Config.GAMEPLAY.targetedReloadSkip.set(List.of(Probe.class.getName()));
        CompletableFuture<LightReload.Result> run;
        try {
            run = LightReload.start(server, false);
        } finally {
            // Both are read when the run starts, so neither has to stay changed while it runs.
            LightReload.restoreRemembered(real);
            Config.GAMEPLAY.targetedReloadSkip.set(skipBefore);
        }

        helper.succeedWhen(() -> {
            helper.assertTrue(run.isDone(), "the targeted reload is still running");
            helper.assertFalse(run.isCompletedExceptionally(), "the targeted reload failed");
            helper.assertTrue(skippedProbe.calls == 0, "the listener named in targetedReloadSkip ran "
                    + skippedProbe.calls + " times, so the config list is not read");
            helper.assertTrue(keptProbe.calls == 1, "the listener not on the list ran " + keptProbe.calls
                    + " times instead of once, so skipping one took others with it");
            helper.assertTrue(run.join().skipped() == 1, "the run counted " + run.join().skipped()
                    + " skipped listeners instead of one");
        });
    }

    @HsGameTest(batch = "targeted_reload_mode")
    public static void theModeComesFromTheConfigUntilTheLabOverridesIt(GameTestHelper helper) {
        StageReloadMode configBefore = Config.GAMEPLAY.reloadAfterStageChange.get();
        StageReloadMode overrideBefore = LightReload.modeOverride();
        try {
            LightReload.setMode(null);
            Config.GAMEPLAY.reloadAfterStageChange.set(StageReloadMode.FULL);
            helper.assertTrue(LightReload.mode() == StageReloadMode.FULL,
                    "with no lab override the mode is " + LightReload.mode() + ", not the config's FULL");
            helper.assertTrue(LightReload.resendRecipes(),
                    "FULL no longer resends the recipe list, so it is not what 6.x shipped any more");

            Config.GAMEPLAY.reloadAfterStageChange.set(StageReloadMode.TARGETED);
            helper.assertFalse(LightReload.resendRecipes(),
                    "TARGETED resends the recipe list, which restarts JEI on every stage change");

            LightReload.setMode(StageReloadMode.BACKGROUND);
            helper.assertTrue(LightReload.mode() == StageReloadMode.BACKGROUND,
                    "the lab override did not win over the config");
            helper.succeed();
        } finally {
            LightReload.setMode(overrideBefore);
            Config.GAMEPLAY.reloadAfterStageChange.set(configBefore);
        }
    }

    /** A second listener class, so one can be skipped by name while the other runs. */
    private static final class OtherProbe implements ResourceManagerReloadListener {
        int calls;

        @Override
        public void onResourceManagerReload(ResourceManager resourceManager) {
            calls++;
        }
    }

    /** A mod's reload listener, reduced to counting. */
    private static final class Probe implements ResourceManagerReloadListener {
        int calls;

        @Override
        public void onResourceManagerReload(ResourceManager resourceManager) {
            calls++;
        }
    }
}
