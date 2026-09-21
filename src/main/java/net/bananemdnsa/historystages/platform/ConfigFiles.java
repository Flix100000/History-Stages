package net.bananemdnsa.historystages.platform;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.GraphConfig;
import net.bananemdnsa.historystages.data.config.ConfigDerivedCaches;
import net.bananemdnsa.historystages.data.config.LegacyConfigMigration;
import net.bananemdnsa.historystages.data.graph.GraphConfigMigration;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Puts the three config files where the other loader puts them and loads them in one place.
 *
 * <p>On NeoForge this is three {@code registerConfig} calls and a pair of config events. Fabric
 * has neither, so the paths are written out here and the work that used to hang off those events
 * hangs off a reload listener per spec instead.
 *
 * <p>The paths are not a free choice. {@code config/historystages/settings/} is where packs,
 * the wiki and the in-game editor already look, and a file written anywhere else would be
 * invisible to all three and would not carry over from a NeoForge world.
 */
public final class ConfigFiles {

    private static final String SETTINGS_DIR = "historystages/settings";

    private ConfigFiles() {}

    public static void loadAll() {
        Path settings = FabricLoader.getInstance().getConfigDir().resolve(SETTINGS_DIR);

        // The derived caches have to be rebuilt on every load, not just the first: the editor
        // writes the file and reloads it, and a cache built from the old values would outlive it.
        Config.VISUAL_SPEC.setReloadListener(() -> {
            ConfigDerivedCaches.rebuildVisual();
            LegacyConfigMigration.apply();
        });
        Config.GAMEPLAY_SPEC.setReloadListener(() -> {
            ConfigDerivedCaches.rebuildGameplay();
            LegacyConfigMigration.apply();
        });
        GraphConfig.GRAPH_SPEC.setReloadListener(GraphConfigMigration::apply);

        // The legacy migration needs both specs and checks for that itself, so it does nothing on
        // the first of these two and runs once on the second. That is why it is hung off both
        // rather than off whichever happens to be loaded last.
        Config.VISUAL_SPEC.load(settings.resolve("visual.toml"));
        Config.GAMEPLAY_SPEC.load(settings.resolve("gameplay.toml"));
        GraphConfig.GRAPH_SPEC.load(settings.resolve("graph.toml"));
    }
}
