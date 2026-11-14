package net.bananemdnsa.historystages.data.config;

import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Watches the settings folder and reports a changed file once it has stopped being written.
 *
 * <p>On 1.21.1 NeoForge's config watcher did this for the three settings files: an admin editing
 * {@code gameplay.toml} by hand on a running server saw the change without a restart, and the
 * derived caches were rebuilt from the Reloading event. The platform specs have no watcher of
 * their own, so this one stands in for it, on the server only.
 */
public final class ConfigWatcher {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Editors write in bursts; one reload per burst, after it has settled. */
    static final long SETTLE_MILLIS = 300;

    private final WatchService service;
    private final Thread thread;
    private final Set<String> pending = ConcurrentHashMap.newKeySet();

    private ConfigWatcher(Path dir, Set<String> names, Consumer<String> onChange, Executor executor) throws IOException {
        this.service = FileSystems.getDefault().newWatchService();
        dir.register(service, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);
        this.thread = new Thread(() -> run(names, onChange, executor), "HistoryStages settings watcher");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    /**
     * Starts watching {@code dir}. {@code onChange} gets the file name and runs on {@code executor}
     * (the server thread), never on the watcher's own. Null when the folder cannot be watched.
     */
    public static @Nullable ConfigWatcher start(Path dir, Set<String> names, Consumer<String> onChange, Executor executor) {
        try {
            return new ConfigWatcher(dir, names, onChange, executor);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("[HistoryStages] Cannot watch {} for changes; edits need /historystages reload", dir, e);
            return null;
        }
    }

    public void stop() {
        try {
            service.close();
        } catch (IOException ignored) {
            // Closing is all that is left to do with it.
        }
        thread.interrupt();
    }

    private void run(Set<String> names, Consumer<String> onChange, Executor executor) {
        try {
            while (true) {
                WatchKey key = service.take();
                collect(key, names);
                // Keep collecting until the folder has been quiet for a moment.
                WatchKey more;
                while ((more = service.poll(SETTLE_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)) != null) {
                    collect(more, names);
                }
                for (String name : Set.copyOf(pending)) {
                    pending.remove(name);
                    executor.execute(() -> onChange.accept(name));
                }
            }
        } catch (InterruptedException | ClosedWatchServiceException e) {
            // Stopped.
        }
    }

    private void collect(WatchKey key, Set<String> names) {
        for (WatchEvent<?> event : key.pollEvents()) {
            if (event.context() instanceof Path changed && names.contains(changed.getFileName().toString())) {
                pending.add(changed.getFileName().toString());
            }
        }
        key.reset();
    }
}
