package net.bananemdnsa.historystages.data.config;

import net.bananemdnsa.historystages.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A hand edit to a settings file on a running server reaches the values, as NeoForge's config
 * watcher made it on 1.21.1: the watcher reports the file, the reload reads it again.
 */
class ConfigWatcherTest {

    @Test
    void anEditedSettingsFileIsReportedOnceAndOthersAreNot(@TempDir Path dir) throws Exception {
        Path gameplay = dir.resolve("gameplay.toml");
        Files.writeString(gameplay, "");
        BlockingQueue<String> reported = new LinkedBlockingQueue<>();
        ConfigWatcher watcher = ConfigWatcher.start(dir, Set.of("gameplay.toml"), reported::add, Runnable::run);
        assertNotNull(watcher);
        try {
            Files.writeString(dir.resolve("notes.txt"), "not a settings file");
            Files.writeString(gameplay, "# first write\n");
            Files.writeString(gameplay, "# second write\n");
            assertEquals("gameplay.toml", reported.poll(10, TimeUnit.SECONDS));
            assertNull(reported.poll(ConfigWatcher.SETTLE_MILLIS * 3, TimeUnit.MILLISECONDS),
                    "one burst of writes is one reload, and other files are ignored");
        } finally {
            watcher.stop();
        }
    }

    @Test
    void aWatchedEditChangesTheLoadedValue(@TempDir Path dir) throws Exception {
        Path gameplay = dir.resolve("gameplay.toml");
        Config.GAMEPLAY_SPEC.load(gameplay);
        boolean before = Config.GAMEPLAY.enableRuntimeLogging.get();
        BlockingQueue<Boolean> seen = new LinkedBlockingQueue<>();
        ConfigWatcher watcher = ConfigWatcher.start(dir, Set.of("gameplay.toml"), name -> {
            Config.GAMEPLAY_SPEC.load(dir.resolve(name));
            seen.add(Config.GAMEPLAY.enableRuntimeLogging.get());
        }, Runnable::run);
        assertNotNull(watcher);
        try {
            String text = Files.readString(gameplay, StandardCharsets.UTF_8);
            String edited = text.replace("enableRuntimeLogging = " + before, "enableRuntimeLogging = " + !before);
            assertEquals(false, edited.equals(text), "fixture key not found: " + List.of(text.split("\n")).size() + " lines");
            Files.writeString(gameplay, edited, StandardCharsets.UTF_8);
            assertEquals(!before, seen.poll(10, TimeUnit.SECONDS));
        } finally {
            watcher.stop();
        }
    }
}
