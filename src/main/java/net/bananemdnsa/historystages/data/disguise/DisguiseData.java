package net.bananemdnsa.historystages.data.disguise;

import net.bananemdnsa.historystages.data.graph.GraphSettingsPaths;
import net.bananemdnsa.historystages.util.DebugLogger;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Holds the current {@link DisguiseRuleSet} and reads/writes {@code settings/disguises.json}.
 *
 * <p>On the server the set comes from the file; on the client from
 * {@code SyncStageDefinitionsPacket}. {@link #version()} moves on every {@link #set}, which is how
 * the client notices that its block table is stale.
 */
public final class DisguiseData {

    public static final String FILE = "disguises.json";

    private static volatile DisguiseRuleSet current = DisguiseRuleSet.empty();
    private static volatile int version;

    private DisguiseData() {}

    public static DisguiseRuleSet get() {
        return current;
    }

    public static void set(DisguiseRuleSet set) {
        current = set != null ? set : DisguiseRuleSet.empty();
        version++;
    }

    public static int version() {
        return version;
    }

    /** Loads the file; returns what was wrong with it, for the stage load messages. */
    public static List<String> load() {
        File file = GraphSettingsPaths.file(FILE);
        if (!file.exists()) {
            set(DisguiseRuleSet.empty());
            return List.of();
        }
        try {
            DisguiseRuleSet.Parsed parsed = DisguiseRuleSet.fromJson(
                    new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            set(parsed.set());
            parsed.problems().forEach(p -> DebugLogger.warn("Disguise", p));
            return parsed.problems();
        } catch (Exception e) {
            DebugLogger.error("Disguise", "Could not read " + FILE + ": " + e.getMessage());
            set(DisguiseRuleSet.empty());
            return List.of("could not read " + FILE + ": " + e.getMessage());
        }
    }

    public static void save() {
        File file = GraphSettingsPaths.file(FILE);
        try {
            if (current.isEmpty()) {
                // No rules, no file: a pack that never used disguises should not gain one.
                Files.deleteIfExists(file.toPath());
                return;
            }
            Files.write(file.toPath(), DisguiseRuleSet.toJson(current).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            DebugLogger.error("Disguise", "Could not write " + FILE + ": " + e.getMessage());
        }
    }
}
