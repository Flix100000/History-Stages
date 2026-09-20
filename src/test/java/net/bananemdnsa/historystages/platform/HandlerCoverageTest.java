package net.bananemdnsa.historystages.platform;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Every class marked as holding handlers has to be registered somewhere.
 *
 * <p>NeoForge finds them by scanning the jar. Nothing does that here, so they are named in a list
 * — and a class left off it fails in the quietest way this port can fail: no error, no warning,
 * just a rule that never applies. That is indistinguishable from a rule that does not match, which
 * is why it gets its own guard rather than being left to an in-game check.
 *
 * <p>A source scan, so it needs neither Minecraft nor a running game.
 */
class HandlerCoverageTest {

    private static final Path SOURCES = Path.of("src", "main", "java");

    /**
     * Marked but deliberately not in either list, each for a reason. An entry here is a decision;
     * a class missing from both this list and the registration lists is an oversight.
     */
    private static final Set<String> REGISTERED_ELSEWHERE = Set.of(
            // Registered only when the mod it integrates with is present.
            "CuriosEquipLockHandler", "AccessoriesEquipLockHandler",
            // An instance handler: it keeps state, so the initializer registers the object.
            "AutoTriggerEventBridge", "HistoryStages",
            // The demo addon is not part of the port yet; it comes with its own round.
            "DemoAddonCategory", "DemoAddonCategoryEditor", "DemoConfigSections",
            "DemoRelicSetEditor", "DemoRequirement", "DemoRequirementEditor", "DemoSettingsGroup",
            // Fires the registration events rather than listening for them.
            "ClientCategoryEditorSetup");

    @Test
    void everyMarkedHandlerIsRegistered() throws IOException {
        Set<String> marked = markedClasses();
        Set<String> listed = listedClasses();

        List<String> missing = new ArrayList<>();
        for (String name : marked) {
            if (REGISTERED_ELSEWHERE.contains(name) || listed.contains(name)) {
                continue;
            }
            missing.add(name);
        }

        if (!missing.isEmpty()) {
            throw new AssertionError(missing.size()
                    + " classes hold event handlers but are never registered, so their rules never"
                    + " apply:\n  " + String.join("\n  ", missing));
        }
    }

    /** Classes carrying the marker and at least one handler method. */
    private static Set<String> markedClasses() throws IOException {
        Set<String> out = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String src = Files.readString(file);
                if (src.contains("@EventBusSubscriber") && src.contains("@SubscribeEvent")) {
                    String name = file.getFileName().toString();
                    out.add(name.substring(0, name.length() - ".java".length()));
                }
            }
        }
        return out;
    }

    /** Class literals in the two registration lists. */
    private static Set<String> listedClasses() throws IOException {
        Set<String> out = new LinkedHashSet<>();
        for (String list : List.of("platform/Handlers.java", "client/ClientHandlers.java")) {
            Path file = SOURCES.resolve("net/bananemdnsa/historystages").resolve(list);
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.trim();
                if (trimmed.endsWith(".class,") || trimmed.endsWith(".class);")) {
                    out.add(trimmed.substring(0, trimmed.indexOf(".class")));
                }
            }
        }
        return out;
    }
}
