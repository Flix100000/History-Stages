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
 * Every event has to be fired by something.
 *
 * <p>On NeoForge the loader raises these; here each one is raised by a fabric callback or a mixin
 * written for it, and an event nobody raises is the worst kind of bug this port can produce. It
 * compiles, the handler is registered, the test suite is green — and the rule simply never
 * applies. Nothing else in the build would notice.
 *
 * <p>A source scan rather than a classpath scan, like the other guards here, so it needs neither
 * Minecraft nor a running game.
 */
class EventCoverageTest {

    private static final Path EVENTS =
            Path.of("src", "main", "java", "net", "bananemdnsa", "historystages", "platform", "event");
    private static final Path SOURCES = Path.of("src", "main", "java");

    /**
     * Events that are declared but deliberately never raised here, each with the reason. An entry
     * in this list is a decision; an event missing from both this list and the firing sites is an
     * oversight.
     */
    private static final Set<String> NOT_RAISED_HERE = Set.of(
            // Abstract bases: only their nested concrete forms are ever raised.
            "PlayerInteractEvent", "PlayerEvent", "AdvancementEvent", "ItemEntityPickupEvent",
            "LivingEvent", "MobEffectEvent", "MobSpawnEvent", "BlockEvent", "LevelEvent",
            "ExplosionEvent", "PlayerTickEvent", "ServerTickEvent", "EntityTeleportEvent");

    @Test
    void everyEventIsRaisedSomewhere() throws IOException {
        Set<String> declared = declaredEventTypes();
        Set<String> raised = raisedEventTypes();

        List<String> dead = new ArrayList<>();
        for (String type : declared) {
            String simple = type.substring(type.lastIndexOf('.') + 1);
            if (NOT_RAISED_HERE.contains(simple) || NOT_RAISED_HERE.contains(type)) {
                continue;
            }
            if (!raised.contains(type) && !raised.contains(simple)) {
                dead.add(type);
            }
        }

        if (!dead.isEmpty()) {
            throw new AssertionError(
                    "these events are declared and handled but never raised, so the rules behind them "
                            + "do nothing at all:\n  " + String.join("\n  ", dead));
        }
    }

    /** Concrete event classes, including the nested ones, by their outer.inner name. */
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
                boolean isAbstract = outer.group(1) != null;
                if (!isAbstract) {
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

    /** Anything handed to EventBus.post, by the type named in the construction. */
    private static Set<String> raisedEventTypes() throws IOException {
        Set<String> out = new LinkedHashSet<>();
        Pattern posted = Pattern.compile("EventBus\\.post\\(\\s*new\\s+([\\w.]+)\\s*\\(");
        Pattern postedVariable = Pattern.compile("(?:new\\s+)([\\w.]+)\\s+\\w+\\s*=\\s*new\\s+\\1\\s*\\(");

        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String src = Files.readString(file);
                if (!src.contains("EventBus.post")) {
                    continue;
                }
                record(out, posted.matcher(src));
                record(out, postedVariable.matcher(src));
                // A two-step raise: build the event, then post the variable.
                Matcher built = Pattern.compile("new\\s+([\\w.]+Event(?:\\.\\w+)?)\\s*\\(").matcher(src);
                record(out, built);
            }
        }
        return out;
    }

    private static void record(Set<String> out, Matcher matcher) {
        while (matcher.find()) {
            String name = matcher.group(1);
            out.add(name);
            out.add(name.substring(name.lastIndexOf('.') + 1));
            // A nested type is often written with its outer name in front.
            int dot = name.lastIndexOf('.');
            if (dot > 0) {
                String tail = name.substring(name.lastIndexOf('.', dot - 1) + 1);
                out.add(tail);
            }
        }
    }
}
