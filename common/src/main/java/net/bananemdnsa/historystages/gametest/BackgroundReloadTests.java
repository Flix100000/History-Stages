package net.bananemdnsa.historystages.gametest;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.reload.BackgroundReload;
import net.bananemdnsa.historystages.reload.LightReload;
import net.bananemdnsa.historystages.reload.StageReloadMode;
import net.minecraft.gametest.framework.GameTestHelper;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.item.crafting.RecipeManager;

/** PROTOTYPE. Holds what the background reload promises: a real reload, and ticks during it. */
public class BackgroundReloadTests {

    public BackgroundReloadTests() {}

    // 26.2's GameTest server ticks as fast as it can: 1200 ticks are over before a real reload is.
    @HsGameTest(batch = "background_reload", timeoutTicks = 24000)
    public static void aBackgroundReloadSwapsTheRecipesWhileTheServerKeepsTicking(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        RecipeManager before = server.getRecipeManager();
        StageReloadMode previous = LightReload.mode();
        LightReload.setMode(StageReloadMode.BACKGROUND);
        try {
            BackgroundReload.request(server);
        } finally {
            LightReload.setMode(previous); // the mode is read when the reload starts
        }

        if (!BackgroundReload.isRunning(server)) {
            helper.fail("the reload was over before request() returned, so the server waited for it");
            return;
        }

        // A reload that blocks the server thread finishes between two ticks, so the test would
        // never see it running. Two ticks is the least that proves the server went on without it.
        int[] ticksWhileRunning = {0};
        helper.onEachTick(() -> {
            if (BackgroundReload.isRunning(server)) ticksWhileRunning[0]++;
        });
        helper.succeedWhen(() -> {
            helper.assertFalse(BackgroundReload.isRunning(server), "the reload is still running");
            helper.assertTrue(ticksWhileRunning[0] >= 2, "the server ticked only " + ticksWhileRunning[0]
                    + " times during the reload, so it was waiting on it after all");
            helper.assertTrue(server.getRecipeManager() != before, "the reload finished without "
                    + "swapping in new recipes, so mods were never told to let go of their copies");
        });
    }

    @HsGameTest(batch = "background_reload_targeted", timeoutTicks = 24000)
    public static void aTargetedReloadKeepsTheRecipesAndDoesNotHoldTheServer(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        RecipeManager before = server.getRecipeManager();
        StageReloadMode previous = LightReload.mode();
        LightReload.setMode(StageReloadMode.TARGETED);
        // The Fabric dev run has no mod that adds a server reload listener, and a replay of nothing
        // is over at once. One listener that prepares off-thread, as real ones do, makes it a reload.
        List<PreparableReloadListener> real = LightReload.remembered();
        LightReload.rememberIfServerData(List.of(before, new BackgroundProbe()));
        try {
            BackgroundReload.request(server);
        } finally {
            LightReload.setMode(previous);
            LightReload.restoreRemembered(real); // the list is read when the reload starts
        }

        if (!BackgroundReload.isRunning(server)) {
            helper.fail("the targeted reload was over before request() returned, so the server waited for it");
            return;
        }
        helper.succeedWhen(() -> {
            helper.assertFalse(BackgroundReload.isRunning(server), "the targeted reload is still running");
            helper.assertTrue(server.getRecipeManager() == before, "the targeted reload swapped in a new "
                    + "recipe manager, so it did the full reload after all");
        });
    }

    /** A mod's listener that prepares on the background pool, the way most real ones do. */
    private static final class BackgroundProbe implements PreparableReloadListener {
        @Override
        public CompletableFuture<Void> reload(SharedState state, Executor backgroundExecutor,
                                              PreparationBarrier barrier, Executor gameExecutor) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }, backgroundExecutor).thenCompose(barrier::wait).thenAcceptAsync(ignored -> { }, gameExecutor);
        }
    }
}
