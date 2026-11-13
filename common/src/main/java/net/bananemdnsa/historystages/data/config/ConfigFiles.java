package net.bananemdnsa.historystages.data.config;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.GraphConfig;
import net.bananemdnsa.historystages.data.graph.GraphConfigMigration;
import net.bananemdnsa.historystages.platform.Services;

import java.nio.file.Path;

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
}
