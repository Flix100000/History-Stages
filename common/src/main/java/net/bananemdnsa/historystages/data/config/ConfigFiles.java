package net.bananemdnsa.historystages.data.config;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.GraphConfig;
import net.bananemdnsa.historystages.data.graph.GraphConfigMigration;
import net.bananemdnsa.historystages.platform.Services;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Loads the three settings files from where 1.21.1 kept them, on both loaders.
 *
 * <p>On NeoForge 1.21.1 this was three {@code registerConfig} calls plus the Loading/Reloading
 * config events. The specs are the platform's own now, so the paths are written out here and the
 * work that hung off those events hangs off one reload listener per spec.
 *
 * <p>{@code config/historystages/settings/} is where packs, the wiki and the editor look; any
 * other place would not carry over from a 1.21.1 world.
 */
public final class ConfigFiles {

    private static final String SETTINGS_DIR = "historystages/settings";
    private static final Set<String> FILES = Set.of("visual.toml", "gameplay.toml", "graph.toml");

    private static ConfigWatcher watcher;

    private ConfigFiles() {}

    public static Path settingsDir() {
        return Services.configDir().resolve(SETTINGS_DIR);
    }

    public static void loadAll() {
        Path settings = settingsDir();

        // Rebuilt on every load, not just the first: the editor writes the file and reloads it.
        Config.VISUAL_SPEC.setReloadListener(() -> {
            ConfigDerivedCaches.rebuildVisual();
            LegacyConfigMigration.apply();
        });
        Config.GAMEPLAY_SPEC.setReloadListener(() -> {
            ConfigDerivedCaches.rebuildGameplay();
            LegacyConfigMigration.apply();
        });
        GraphConfig.GRAPH_SPEC.setReloadListener(GraphConfigMigration::apply);

        // The legacy migration waits for both specs itself and then runs once, which is why it
        // hangs off both rather than whichever is loaded last.
        Config.VISUAL_SPEC.load(settings.resolve("visual.toml"));
        Config.GAMEPLAY_SPEC.load(settings.resolve("gameplay.toml"));
        GraphConfig.GRAPH_SPEC.load(settings.resolve("graph.toml"));
    }

    /** Re-reads one settings file; its reload listener rebuilds what hangs off it. */
    public static void reload(String fileName) {
        Path file = settingsDir().resolve(fileName);
        switch (fileName) {
            case "visual.toml" -> Config.VISUAL_SPEC.load(file);
            case "gameplay.toml" -> Config.GAMEPLAY_SPEC.load(file);
            case "graph.toml" -> GraphConfig.GRAPH_SPEC.load(file);
            default -> throw new IllegalArgumentException("not a settings file: " + fileName);
        }
    }

    /**
     * Picks up hand edits while the server runs, as NeoForge's config watcher did on 1.21.1.
     * {@code onReloaded} runs after each reload, on {@code serverThread}.
     */
    public static synchronized void startWatching(Executor serverThread, Runnable onReloaded) {
        stopWatching();
        watcher = ConfigWatcher.start(settingsDir(), FILES, name -> {
            reload(name);
            onReloaded.run();
        }, serverThread);
    }

    public static synchronized void stopWatching() {
        if (watcher != null) {
            watcher.stop();
            watcher = null;
        }
    }
}
