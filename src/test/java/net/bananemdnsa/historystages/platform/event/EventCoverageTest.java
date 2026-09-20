package net.bananemdnsa.historystages.platform.event;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Every event has to be raised by something.
 *
 * <p>On NeoForge the loader raises these; here each one is raised by a fabric callback or by a
 * mixin written for it. An event nobody raises is the worst bug this port can produce: it
 * compiles, the handler is registered, the suite is green, and the rule simply never applies.
 * Nothing else in the build would notice.
 *
 * <p>A source scan rather than a classpath scan, like the other guards here, so it needs neither
 * Minecraft nor a running game.
 *
 * <p><strong>Nested events are matched on their full {@code Outer.Inner} name, never on the inner
 * name alone.</strong> Four events are called {@code Post} and two {@code Pre}; matching on the
 * short name let {@code ItemEntityPickupEvent.Post} count as raised because something else had
 * raised a {@code ServerTickEvent.Post}. The cost is that a raise written as a bare
 * {@code new Post(...)} is not seen, which is why they are all written out in full.
 */
class EventCoverageTest {

    private static final Path EVENTS =
            Path.of("src", "main", "java", "net", "bananemdnsa", "historystages", "platform", "event");
    private static final Path SOURCES = Path.of("src", "main", "java");

    /**
     * Declared but deliberately never raised, each for a reason. An entry here is a decision; an
     * event missing from both this list and the raising sites is an oversight.
     */
    private static final Set<String> NOT_RAISED_HERE = Set.of(
            // Abstract bases — only their concrete nested forms are ever raised.
            "PlayerInteractEvent", "PlayerEvent", "AdvancementEvent", "ItemEntityPickupEvent",
            "LivingEvent", "MobEffectEvent", "MobSpawnEvent", "BlockEvent", "LevelEvent",
            "ExplosionEvent", "PlayerTickEvent", "ServerTickEvent",
            // Only the EnderPearl form is raised; the general teleport has no lock behind it.
            "EntityTeleportEvent");

    @Test
    void everyEventIsRaisedSomewhere() throws IOException {
        Set<String> declared = declaredEventTypes();
        Set<String> raised = raisedEventTypes();

        List<String> dead = new ArrayList<>();
        for (String type : declared) {
            if (NOT_RAISED_HERE.contains(type)) {
                continue;
            }
            if (!raised.contains(type)) {
                dead.add(type);
            }
        }

        if (!dead.isEmpty()) {
            throw new AssertionError(dead.size()
                    + " events are declared and handled but never raised, so the rules behind them do"
                    + " nothing at all:\n  " + String.join("\n  ", dead));
        }
    }

    /** Concrete event types, nested ones as {@code Outer.Inner}. */
    private static Set<String> declaredEventTypes() throws IOException {
        Set<String> out = new LinkedHashSet<>();
        Pattern nested = Pattern.compile(
                "^\\s{4}public (?:static )?(?:final )?class (\\w+)", Pattern.MULTILINE);
        Pattern top = Pattern.compile(
                "^public (?:final )?(abstract )?class (\\w+)", Pattern.MULTILINE);

        try (Stream<Path> files = Files.walk(EVENTS)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String src = Files.readString(file);
                Matcher outer = top.matcher(src);
                if (!outer.find()) {
                    continue;
                }
                String outerName = outer.group(2);
                if (outer.group(1) == null) {
                    out.add(outerName);
                }
                Matcher inner = nested.matcher(src);
                while (inner.find()) {
                    out.add(outerName + "." + inner.group(1));
                }
            }
        }
        return out;
    }

    /** Event types constructed in a file that also posts to the bus. */
    private static Set<String> raisedEventTypes() throws IOException {
        Set<String> out = new LinkedHashSet<>();
        Pattern built = Pattern.compile("new\\s+((?:\\w+\\.)*\\w+Event(?:\\.\\w+)?)\\s*\\(");

        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String src = Files.readString(file);
                if (!src.contains("EventBus.post")) {
                    continue;
                }
                Matcher matcher = built.matcher(src);
                while (matcher.find()) {
                    out.add(trimPackage(matcher.group(1)));
                }
            }
        }
        return out;
    }

    /**
     * Drops the package but keeps the outer type: {@code a.b.LevelEvent.Load} becomes
     * {@code LevelEvent.Load}, and {@code a.b.AnvilUpdateEvent} becomes {@code AnvilUpdateEvent}.
     * Package parts start lower case, type names upper case, which is what tells them apart.
     */
    private static String trimPackage(String name) {
        String[] parts = name.split("\\.");
        List<String> kept = new ArrayList<>();
        for (String part : parts) {
            if (!kept.isEmpty() || Character.isUpperCase(part.charAt(0))) {
                kept.add(part);
            }
        }
        return String.join(".", kept);
    }
}
