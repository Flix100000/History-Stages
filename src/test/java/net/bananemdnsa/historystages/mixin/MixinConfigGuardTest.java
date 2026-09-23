package net.bananemdnsa.historystages.mixin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A class in the mixin package that the config does not name cannot be loaded at all.
 *
 * <p>Mixin hands a config ownership of its whole package and then refuses every class load from
 * it that the config did not declare. The refusal is an {@code IllegalClassLoadError}, and it
 * arrives the first time something touches the class — for an accessor that is the moment a
 * player uses the block it reads, not startup, so a broken build looks entirely healthy until
 * someone opens the wrong screen.
 *
 * <p>{@code ItemCombinerMenuAccessor} shipped unlisted in 5.2.0 and 5.2.1 and crashed the client
 * on opening an anvil, which is where {@code AnvilUpdateMixin} casts to it. That is Issue #103.
 * Nothing in the source says the two files belong together: the cast compiles, the mixin applies,
 * and only the load fails. The config is where the connection is written down, so this reads it.
 */
class MixinConfigGuardTest {

    private static final Path SOURCES =
            Path.of("src", "main", "java", "net", "bananemdnsa", "historystages", "mixin");
    private static final Path CONFIG =
            Path.of("src", "main", "resources", "historystages.mixins.json");

    @Test
    void everyClassInTheMixinPackageIsNamedInTheConfig() throws IOException {
        Set<String> missing = new TreeSet<>(onDisk());
        missing.removeAll(named());

        assertTrue(missing.isEmpty(),
                "these sit in the mixin package unnamed by the config, so the first class load of "
                        + "one throws IllegalClassLoadError in front of a player:\n"
                        + String.join("\n", missing));
    }

    @Test
    void everyNameInTheConfigHasAClass() throws IOException {
        Set<String> orphans = new TreeSet<>(named());
        orphans.removeAll(onDisk());

        assertTrue(orphans.isEmpty(),
                "the config names these and no such class exists, which fails the config as a "
                        + "whole while the game is still starting:\n" + String.join("\n", orphans));
    }

    /** Every mixin the config declares, on whichever side it declares it. */
    private static Set<String> named() throws IOException {
        JsonObject config = new Gson().fromJson(Files.readString(CONFIG), JsonObject.class);
        Set<String> names = new TreeSet<>();
        for (String side : List.of("mixins", "client", "server")) {
            JsonArray declared = config.getAsJsonArray(side);
            if (declared == null) continue;
            declared.forEach(name -> names.add(name.getAsString()));
        }
        return names;
    }

    /** Every class file under the package the config owns, named the way the config names them. */
    private static Set<String> onDisk() throws IOException {
        assertTrue(Files.isDirectory(SOURCES), "expected the test to run from the project root");
        try (Stream<Path> files = Files.walk(SOURCES)) {
            return files.filter(file -> file.toString().endsWith(".java"))
                    .map(file -> {
                        String relative = SOURCES.relativize(file).toString();
                        return relative.substring(0, relative.length() - ".java".length())
                                .replace(File.separatorChar, '.');
                    })
                    .collect(Collectors.toCollection(TreeSet::new));
        }
    }
}
