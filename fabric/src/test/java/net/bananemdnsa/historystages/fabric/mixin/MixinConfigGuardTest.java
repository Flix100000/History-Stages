package net.bananemdnsa.historystages.fabric.mixin;

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
 * Keeps the mixin config and its package in step (68f5c6d3 on fabric-1.21.X).
 *
 * <p>Mixin gives a config its whole package and refuses to load any class from it that the config
 * does not name. The refusal is an IllegalClassLoadError the first time something touches the
 * class: for an accessor, the moment a player uses the block it reads. {@code
 * ItemCombinerMenuAccessor} shipped unlisted once and crashed the client on opening an anvil
 * (Issue #103). A name with no class fails the whole config at startup instead.
 *
 * <p>Third check: a mixin on a client class listed outside "client" crashes a dedicated server at
 * startup, so everything under {@code mixin/client} must be in the client list and nothing else.
 */
class MixinConfigGuardTest {

    private static final Path SOURCES = Path.of("src", "main", "java",
            "net", "bananemdnsa", "historystages", "fabric", "mixin");
    private static final Path CONFIG = Path.of("src", "main", "resources", "historystages-fabric.mixins.json");

    @Test
    void everyClassInTheMixinPackageIsNamedInTheConfig() throws IOException {
        Set<String> missing = new TreeSet<>(onDisk());
        missing.removeAll(named(List.of("mixins", "client", "server")));
        assertTrue(missing.isEmpty(), "in the mixin package but not in the config, so the first class load "
                + "of one throws IllegalClassLoadError in front of a player:\n" + String.join("\n", missing));
    }

    @Test
    void everyNameInTheConfigHasAClass() throws IOException {
        Set<String> orphans = new TreeSet<>(named(List.of("mixins", "client", "server")));
        orphans.removeAll(onDisk());
        assertTrue(orphans.isEmpty(), "named by the config with no such class, which fails the config "
                + "while the game is still starting:\n" + String.join("\n", orphans));
    }

    @Test
    void clientMixinsAreOnlyInTheClientList() throws IOException {
        Set<String> wrong = new TreeSet<>();
        for (String name : named(List.of("mixins", "server"))) {
            if (name.startsWith("client.")) {
                wrong.add(name + " (common list, would load on a dedicated server)");
            }
        }
        for (String name : named(List.of("client"))) {
            if (!name.startsWith("client.")) {
                wrong.add(name + " (client list, would never apply on a dedicated server)");
            }
        }
        assertTrue(wrong.isEmpty(), "mixins on the wrong side:\n" + String.join("\n", wrong));
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
        assertTrue(Files.isDirectory(SOURCES), "expected the test to run from the fabric project directory");
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
