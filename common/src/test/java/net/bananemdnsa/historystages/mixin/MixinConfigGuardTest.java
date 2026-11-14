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
 * Keeps {@code historystages.mixins.json} and the common mixin package in step, like the Fabric
 * module's guard does for its own config. Mixin refuses to load an unlisted class from a config's
 * package (IllegalClassLoadError the first time it is touched), and a listed name without a class
 * fails the config at startup. The NeoForge module has no mixin config of its own.
 */
class MixinConfigGuardTest {

    private static final Path SOURCES = Path.of("src", "main", "java",
            "net", "bananemdnsa", "historystages", "mixin");
    private static final Path CONFIG = Path.of("src", "main", "resources", "historystages.mixins.json");

    /** Not mixins: the config plugin, which Mixin loads itself. */
    private static final Set<String> NOT_MIXINS = Set.of("HistoryStagesMixinPlugin");

    @Test
    void everyClassInTheMixinPackageIsNamedInTheConfig() throws IOException {
        Set<String> missing = new TreeSet<>(onDisk());
        missing.removeAll(named(List.of("mixins", "client", "server")));
        assertTrue(missing.isEmpty(), "in the mixin package but not in the config:\n" + String.join("\n", missing));
    }

    @Test
    void everyNameInTheConfigHasAClass() throws IOException {
        Set<String> orphans = new TreeSet<>(named(List.of("mixins", "client", "server")));
        orphans.removeAll(onDisk());
        assertTrue(orphans.isEmpty(), "named by the config with no such class:\n" + String.join("\n", orphans));
    }

    @Test
    void thePluginTheConfigNamesExists() throws IOException {
        JsonObject config = new Gson().fromJson(Files.readString(CONFIG), JsonObject.class);
        String plugin = config.get("plugin").getAsString();
        Path file = Path.of("src", "main", "java", plugin.replace('.', File.separatorChar) + ".java");
        assertTrue(Files.isRegularFile(file), "mixin plugin " + plugin + " has no source file");
    }

    private static Set<String> named(List<String> sides) throws IOException {
        JsonObject config = new Gson().fromJson(Files.readString(CONFIG), JsonObject.class);
        Set<String> names = new TreeSet<>();
        for (String side : sides) {
            JsonArray declared = config.getAsJsonArray(side);
            if (declared != null) {
                declared.forEach(name -> names.add(name.getAsString()));
            }
        }
        return names;
    }

    private static Set<String> onDisk() throws IOException {
        assertTrue(Files.isDirectory(SOURCES), "expected the test to run from the common project directory");
        try (Stream<Path> files = Files.walk(SOURCES)) {
            return files.filter(file -> file.toString().endsWith(".java"))
                    .map(file -> {
                        String relative = SOURCES.relativize(file).toString();
                        return relative.substring(0, relative.length() - ".java".length())
                                .replace(File.separatorChar, '.');
                    })
                    .filter(name -> !NOT_MIXINS.contains(name))
                    .collect(Collectors.toCollection(TreeSet::new));
        }
    }
}
