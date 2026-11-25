package net.bananemdnsa.historystages.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.reload.LightReload;
import net.bananemdnsa.historystages.reload.StageReloadMode;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.crafting.RecipeManager;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;

/** PROTOTYPE. Holds what the light reload promises: mods hear about it, vanilla does not. */
public class LightReloadTests {

    public LightReloadTests() {}

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
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

    /**
     * Fabric API adds the listeners mods register through {@code ResourceManagerHelper} inside
     * {@code SimpleReloadInstance.create}, not before it. A hook around the whole method keeps a
     * list without them, and the targeted reload then has nothing of the mods' to run.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "fabric_reload_listeners", timeoutTicks = 600)
    public static void listenersRegisteredThroughFabricApiAreRemembered(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        FabricProbe probe = FabricProbe.register();
        int callsBefore = probe.calls;
        CompletableFuture<Void> reload = server.reloadResources(server.getPackRepository().getSelectedIds());
        helper.succeedWhen(() -> {
            helper.assertTrue(reload.isDone(), "the datapack reload is still running");
            helper.assertFalse(reload.isCompletedExceptionally(), "the datapack reload failed");
            helper.assertTrue(probe.calls > callsBefore, "the reload never ran the probe, so it was not "
                    + "registered and the test proves nothing");
            helper.assertTrue(LightReload.remembered().contains(probe), "a listener registered through "
                    + "Fabric API is missing from the remembered list, so a targeted reload skips it");
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "targeted_reload", timeoutTicks = 400)
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

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "targeted_reload_skip", timeoutTicks = 400)
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

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "targeted_reload_mode")
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

    /** A listener registered the way Fabric mods register theirs. */
    private static final class FabricProbe implements ResourceManagerReloadListener, IdentifiableResourceReloadListener {
        static final FabricProbe INSTANCE = new FabricProbe();
        private static boolean registered;
        int calls;

        static synchronized FabricProbe register() {
            if (!registered) {
                ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(INSTANCE);
                registered = true;
            }
            return INSTANCE;
        }

        @Override
        public ResourceLocation getFabricId() {
            return ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "gametest_reload_probe");
        }

        @Override
        public void onResourceManagerReload(ResourceManager resourceManager) {
            calls++;
        }
    }
}
