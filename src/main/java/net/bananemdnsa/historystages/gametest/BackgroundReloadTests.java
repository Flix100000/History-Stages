package net.bananemdnsa.historystages.gametest;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.reload.BackgroundReload;
import net.bananemdnsa.historystages.reload.LightReload;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** PROTOTYPE. Holds what the background reload promises: a real reload, and ticks during it. */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BackgroundReloadTests {

    private BackgroundReloadTests() {}

    @GameTest(template = "empty", batch = "background_reload", timeoutTicks = 1200)
    public static void aBackgroundReloadSwapsTheRecipesWhileTheServerKeepsTicking(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        RecipeManager before = server.getRecipeManager();
        LightReload.Mode previous = LightReload.mode();
        LightReload.setMode(LightReload.Mode.BACKGROUND);
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

    @GameTest(template = "empty", batch = "background_reload_targeted", timeoutTicks = 1200)
    public static void aTargetedReloadKeepsTheRecipesAndDoesNotHoldTheServer(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        RecipeManager before = server.getRecipeManager();
        LightReload.Mode previous = LightReload.mode();
        LightReload.setMode(LightReload.Mode.TARGETED);
        try {
            BackgroundReload.request(server);
        } finally {
            LightReload.setMode(previous);
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
}
