package net.bananemdnsa.historystages.data.config;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.GraphConfig;
import net.bananemdnsa.historystages.platform.ModConfigSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@code settings/} folder written by NeoForge 6.1.0 on 1.21.1 loads on 26.2 as it is: the
 * values come through, and a valid file is not rewritten just by loading it (review focus 1).
 */
class LegacySettingsLoadTest {

    private static Path copyFixture(Path dir, String name) throws IOException {
        Path target = dir.resolve(name);
        try (InputStream in = LegacySettingsLoadTest.class.getResourceAsStream("/fixtures/settings-6.1.0/" + name)) {
            assertTrue(in != null, "fixture " + name + " is missing");
            Files.copy(in, target);
        }
        return target;
    }

    private static void assertLoadsUnchanged(ModConfigSpec spec, Path file) throws IOException {
        byte[] before = Files.readAllBytes(file);
        spec.load(file);
        assertArrayEquals(before, Files.readAllBytes(file), file.getFileName() + " was rewritten by loading it");
    }

    @Test
    void aSixOneSettingsFolderLoadsWithoutBeingRewritten(@TempDir Path dir) throws IOException {
        assertLoadsUnchanged(Config.GAMEPLAY_SPEC, copyFixture(dir, "gameplay.toml"));
        assertLoadsUnchanged(Config.VISUAL_SPEC, copyFixture(dir, "visual.toml"));
        assertLoadsUnchanged(GraphConfig.GRAPH_SPEC, copyFixture(dir, "graph.toml"));
    }

    @Test
    void aValueOtherThanTheDefaultComesThrough(@TempDir Path dir) throws IOException {
        // The fixture turns runtime logging on; the default is off.
        Config.GAMEPLAY_SPEC.load(copyFixture(dir, "gameplay.toml"));
        assertEquals(false, Config.GAMEPLAY.enableRuntimeLogging.getDefault());
        assertEquals(true, Config.GAMEPLAY.enableRuntimeLogging.get());
    }

    @Test
    void aSavedSettingSurvivesAReload(@TempDir Path dir) throws IOException {
        // The trap from 1.21.1 (a client setting that was never saved): set, save, load again.
        Path file = copyFixture(dir, "gameplay.toml");
        Config.GAMEPLAY_SPEC.load(file);
        boolean flipped = !Config.GAMEPLAY.enableRuntimeLogging.get();
        Config.GAMEPLAY.enableRuntimeLogging.set(flipped);
        Config.GAMEPLAY_SPEC.save();

        Config.GAMEPLAY_SPEC.load(file);
        assertEquals(flipped, Config.GAMEPLAY.enableRuntimeLogging.get());
    }
}
