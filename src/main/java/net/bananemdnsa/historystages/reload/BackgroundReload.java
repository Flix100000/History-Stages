package net.bananemdnsa.historystages.reload;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.logging.LogUtils;

import net.bananemdnsa.historystages.util.DebugLogger;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

/**
 * PROTOTYPE. Runs the reload a stage change needs without the server waiting for it.
 *
 * <p><strong>Background (full).</strong> {@code MinecraftServer.reloadResources} is already a
 * chain of futures: packs are opened on the server thread, every listener prepares on the
 * background pool and applies on the server thread, and the finished resources are swapped in by
 * one last server task. The only thing that turns this into a freeze is its last line — when the
 * caller is the server thread, it parks that thread in {@code managedBlock} until the chain is
 * done. On a 500-mod pack that is seconds without a tick, long enough for the watchdog to take the
 * server down. Calling it from any other thread skips that line and nothing else: same listeners,
 * same swap, every mod hears it the way it hears {@code /reload}.
 *
 * <p><strong>Targeted.</strong> Only the mods' own reload listeners, run again against the
 * resources already loaded, plus a tags event — see {@link LightReload}. No pack is opened, no
 * recipe parsed, nothing swapped. Falls back to the background reload when no reload has been
 * seen to replay yet.
 *
 * <p>The server ticks while either runs, and that is fine for a reason particular to a stage
 * change: no file changed, so the recipes are the same recipes. The gate is asked on every lookup,
 * so the recipe manager in use already answers with the new stage in force — a machine that
 * rebuilds its cache in the meantime gets the same list it would get afterwards.
 */
public final class BackgroundReload {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The server a reload is running on, or null. Kept per server so a new world starts clean. */
    private static MinecraftServer running;
    /** A stage change arrived while a reload was running, so one more is owed after it. */
    private static boolean owed;
    private static final List<Runnable> onIdle = new ArrayList<>();

    private BackgroundReload() {}

    /**
     * Starts a reload of the current {@link LightReload#mode()}, or owes one if a reload is already
     * under way. Server thread only.
     *
     * <p>Owed rather than ignored: the running reload may already be past a listener that let go
     * of a cache, and a machine may already have rebuilt that cache under the old stage.
     */
    public static synchronized void request(MinecraftServer server) {
        if (running == server) {
            owed = true;
            return;
        }
        start(server);
    }

    /** Whether a reload started here is still running on this server. */
    public static synchronized boolean isRunning(MinecraftServer server) {
        return running == server;
    }

    /** Runs {@code task} on the server thread once no reload is running — at once if none is. */
    public static synchronized void whenIdle(MinecraftServer server, Runnable task) {
        if (running == server) {
            onIdle.add(task);
        } else {
            task.run();
        }
    }

    /** Server going away: nothing that is running will finish. */
    public static synchronized void forget() {
        running = null;
        owed = false;
        onIdle.clear();
    }

    private static void start(MinecraftServer server) {
        running = server;
        owed = false;
        long startNanos = System.nanoTime();

        boolean targeted = LightReload.mode() == StageReloadMode.TARGETED;
        if (targeted && !LightReload.hasRemembered()) {
            LOGGER.info("[HistoryStages/BackgroundReload] No reload seen to replay yet, doing the full one");
            targeted = false;
        }

        CompletableFuture<?> work;
        if (targeted) {
            work = LightReload.start(server, true);
        } else {
            // Read here, on the server thread: the pack list is not meant to be read from elsewhere.
            Collection<String> packs = List.copyOf(server.getPackRepository().getSelectedIds());
            // reloadResources only builds the chain and returns, so the pool thread is not held up.
            work = CompletableFuture.supplyAsync(() -> server.reloadResources(packs), Util.backgroundExecutor())
                    .thenCompose(reload -> reload);
        }
        String kind = targeted ? "targeted" : "background";
        // Hopped onto the server thread so the next reload starts there, like the first.
        work.whenCompleteAsync((ignored, error) -> finish(server, kind, startNanos, error), server);
    }

    private static void finish(MinecraftServer server, String kind, long startNanos, Throwable error) {
        List<Runnable> idleTasks;
        synchronized (BackgroundReload.class) {
            if (running != server) return; // the server stopped in the meantime
            running = null;
            if (owed) {
                start(server);
                idleTasks = List.of();
            } else {
                idleTasks = List.copyOf(onIdle);
                onIdle.clear();
                ReloadStats.end(error);
            }
        }

        long millis = (System.nanoTime() - startNanos) / 1_000_000;
        if (error != null) {
            DebugLogger.warn("Recipe Locks", "Reloading after a stage change failed (" + kind + "): "
                    + error.getMessage() + ". Machines that keep their own copy of the recipe list may "
                    + "still be going by the old one until /reload.");
        } else {
            LOGGER.info("[HistoryStages/BackgroundReload] {} reload after a stage change finished after {} ms, "
                    + "the server kept ticking meanwhile", kind, millis);
        }
        idleTasks.forEach(Runnable::run);
    }
}
