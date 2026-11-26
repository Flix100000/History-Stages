package net.bananemdnsa.historystages.fabric.gametest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.reload.LightReload;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;

/**
 * The targeted reload against listeners registered the Fabric way. Fabric API adds them inside
 * {@code SimpleReloadInstance.create}, so a hook around the whole method would never see them;
 * and they may read the registries from the shared state, which only vanilla's listeners fill.
 */
public class ReloadListenerTests {

    // One test, not two: both need a datapack reload of their own, and two at once in one batch
    // would race each other.
    @GameTest(maxTicks = 24000)
    public void fabricListenersAreRememberedAndGetTheRegistriesOnReplay(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        FabricProbe probe = FabricProbe.register();
        probe.reset();
        CompletableFuture<Void> reload = server.reloadResources(server.getPackRepository().getSelectedIds());
        CompletableFuture<Integer> fullRunCalls = reload.thenApplyAsync(ignored -> probe.calls, server);
        CompletableFuture<LightReload.Result> replay = fullRunCalls.thenComposeAsync(ignored -> {
            probe.reset();
            return LightReload.start(server, false);
        }, server);
        helper.succeedWhen(() -> {
            helper.assertTrue(replay.isDone(), "the reloads are still running");
            helper.assertFalse(reload.isCompletedExceptionally(), "the datapack reload failed");
            helper.assertTrue(fullRunCalls.join() > 0, "the datapack reload never ran the probe, so it was "
                    + "not registered and the test proves nothing");
            helper.assertTrue(LightReload.remembered().contains(probe), "a listener registered through "
                    + "Fabric API is missing from the remembered list, so a targeted reload skips it");
            helper.assertFalse(replay.isCompletedExceptionally(), "the targeted reload failed");
            helper.assertTrue(probe.calls == 1, "the targeted reload ran the Fabric listener "
                    + probe.calls + " times instead of once");
            helper.assertTrue(probe.sawRegistries, "the Fabric listener found no registries in the shared "
                    + "state, so a mod reading them would throw on every stage change");
        });
    }

    /** A listener registered the way Fabric mods register theirs, reading what they read. */
    private static final class FabricProbe implements PreparableReloadListener {
        static final FabricProbe INSTANCE = new FabricProbe();
        private static boolean registered;
        volatile int calls;
        volatile boolean sawRegistries;

        static synchronized FabricProbe register() {
            if (!registered) {
                ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(
                        Identifier.fromNamespaceAndPath(HistoryStages.MOD_ID, "gametest_reload_probe"), INSTANCE);
                registered = true;
            }
            return INSTANCE;
        }

        void reset() {
            calls = 0;
            sawRegistries = false;
        }

        @Override
        public CompletableFuture<Void> reload(SharedState state, Executor backgroundExecutor,
                                              PreparationBarrier barrier, Executor gameExecutor) {
            HolderLookup.Provider registries;
            try {
                registries = state.get(ResourceLoader.REGISTRY_LOOKUP_KEY);
            } catch (RuntimeException e) {
                registries = null;
            }
            HolderLookup.Provider seen = registries;
            return barrier.wait(null).thenRunAsync(() -> {
                calls++;
                sawRegistries = seen != null;
            }, gameExecutor);
        }
    }
}
