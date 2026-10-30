package net.bananemdnsa.historystages.util;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.api.diagnostics.RuntimeCategory;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.api.lock.LockCategory;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.config.ConfigSpecCodec;
import net.bananemdnsa.historystages.data.diagnostics.AddonRegistrations;
import net.bananemdnsa.historystages.data.diagnostics.ReportSections;
import net.bananemdnsa.historystages.data.lock.category.LockCategories;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Dedicated debug logger for History Stages.
 * Two modes:
 * - Load-time report: debug-*.log, written on every server start; the text itself comes from
 *   {@link DebugReport}. Errors and warnings are repeated in latest.log.
 * - Runtime log: timestamped event log in runtime-*.log, buffered and flushed periodically,
 *   only while enableRuntimeLogging is on
 */
public class DebugLogger {

    // Resolved lazily (not a static final field) so that classes calling error()/warn()/info() —
    // which only touch the in-memory issue list — can be loaded without FabricLoader on the
    // classpath. That keeps pure/testable code (e.g. GraphLayoutData.fromJson) safe to unit-test
    // without a running game. The game logger is lazy for the same reason.
    private static Path logsPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("historystages").resolve("logs");
    }
    private static Logger gameLog() {
        return LoggerFactory.getLogger("HistoryStages");
    }
    private static final String LOGS_DISPLAY_PATH = "config/historystages/logs/";
    private static final int MAX_LOG_FILES = 10;
    private static final int MAX_RUNTIME_FILES = 7;
    private static final DateTimeFormatter FILE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * The mods History Stages has compat code for. Only these go into the report header — the
     * full mod list is in latest.log already, and pasting two hundred lines here would bury the
     * part people actually read.
     */
    private static final List<String> COMPAT_MOD_IDS = List.of(
            "lootr", "jei", "emi", "jeresources", "jade", "ftbteams", "ftbquests", "curios",
            "accessories", "spell_engine", "bettercombat", "kubejs", "crafttweaker", "polymorph",
            "fastsuite", "reliable_remover", "create", "mekanism");

    // ==================== Load-time report ====================

    private record Logged(DebugReport.Issue issue, boolean registryPass) {}

    private static final List<Logged> ISSUES = new ArrayList<>();
    /** True while validateAgainstRegistries runs; see {@link #beginRegistryPass()}. */
    private static boolean inRegistryPass = false;
    private static int stagesLoaded = 0;
    /** The stage file being checked right now, stamped onto every issue logged meanwhile. */
    private static String currentSource = null;

    public enum Level {
        ERROR, WARN, INFO
    }

    public static synchronized void clear() {
        ISSUES.clear();
        stagesLoaded = 0;
        currentSource = null;
        inRegistryPass = false;
    }

    public static void setStagesLoaded(int count) {
        stagesLoaded = count;
    }

    /**
     * Marks the start of checks that belong to one stage file. Everything logged until
     * {@link #endSource()} is filed under it in the report, so a message does not have to
     * name its own file — and a bug report shows straight away which file to send along.
     *
     * @param path relative to {@code config/historystages/}, e.g. {@code global/ores/iron.json}
     */
    public static synchronized void beginSource(String path) {
        currentSource = path;
    }

    public static synchronized void endSource() {
        currentSource = null;
    }

    public static void log(String category, Level level, String message) {
        log(category, level, message, null);
    }

    /** @param source the stage file this belongs to, or null for the file currently being checked */
    public static synchronized void log(String category, Level level, String message, String source) {
        ISSUES.add(new Logged(new DebugReport.Issue(level, category, source != null ? source : currentSource, message),
                inRegistryPass));
    }

    /**
     * The registry checks run on every world start, but stage loading — which clears this log —
     * runs once per game. Without this, a second world in the same session listed every registry
     * problem twice.
     */
    public static synchronized void beginRegistryPass() {
        ISSUES.removeIf(Logged::registryPass);
        inRegistryPass = true;
    }

    public static synchronized void endRegistryPass() {
        inRegistryPass = false;
    }

    public static void error(String category, String message) {
        log(category, Level.ERROR, message);
    }

    public static void warn(String category, String message) {
        log(category, Level.WARN, message);
    }

    public static void info(String category, String message) {
        log(category, Level.INFO, message);
    }

    static synchronized List<DebugReport.Issue> issuesForTest() {
        return ISSUES.stream().map(Logged::issue).toList();
    }

    public static void writeLogFile(Map<String, StageEntry> stages, Map<String, StageEntry> individualStages) {
        List<DebugReport.Issue> issues;
        synchronized (DebugLogger.class) {
            issues = ISSUES.stream().map(Logged::issue).toList();
        }

        String fileName = "debug-" + LocalDateTime.now().format(FILE_FORMAT) + ".log";
        try {
            File logsDir = logsPath().toFile();
            if (!logsDir.exists()) logsDir.mkdirs();
            cleanupOldLogs(logsDir, "debug-", MAX_LOG_FILES);

            // Sections first: one that throws counts against its addon in the list built next.
            List<DebugReport.Section> sections = ReportSections.collect();
            DebugReport.Snapshot snapshot = new DebugReport.Snapshot(
                    LocalDateTime.now().format(DISPLAY_FORMAT),
                    versions(),
                    compatMods(),
                    addons(),
                    stagesLoaded,
                    individualStages != null ? individualStages.size() : 0,
                    issues,
                    sections);

            try (PrintWriter pw = new PrintWriter(new FileWriter(new File(logsDir, fileName), StandardCharsets.UTF_8))) {
                for (String line : DebugReport.render(snapshot)) pw.println(line);
            }
        } catch (Exception e) {
            gameLog().error("Failed to write the History Stages debug report: {}", e.toString());
        }

        // latest.log is where most people look first. If History Stages had something to say,
        // it has to be there too, with a pointer to the full report.
        Logger log = gameLog();
        for (DebugReport.LogLine line : DebugReport.latestLogLines(issues, LOGS_DISPLAY_PATH + fileName)) {
            switch (line.level()) {
                case ERROR -> log.error(line.message());
                case WARN -> log.warn(line.message());
                case INFO -> log.info(line.message());
            }
        }
    }

    /**
     * History Stages' own report sections, registered the way an addon's are. Must run before the
     * section register event, so they come first among the built-ins.
     */
    public static void registerBuiltInSections() {
        ReportSections.registerBuiltIn("historystages:lock_entries", "Lock entries across global stages", out -> {
            for (Map.Entry<String, Integer> e : categoryTotals(StageManager.getStages()).entrySet()) {
                if (e.getValue() > 0) out.value(e.getKey(), e.getValue());
            }
        });
        ReportSections.registerBuiltIn("historystages:config", "Config (changed from default)", out -> {
            for (ConfigFile file : configSnapshot()) {
                out.line("[" + file.name() + "] " + file.changed().size() + " of " + file.total() + " changed");
                for (Map.Entry<String, String> v : file.changed().entrySet()) out.line("  " + v.getKey() + " = " + v.getValue());
            }
        });
    }

    private static List<DebugReport.AddonLine> addons() {
        List<DebugReport.AddonLine> out = new ArrayList<>();
        for (AddonRegistrations.AddonSummary a : AddonRegistrations.summaries(FabricLoader.getInstance()::isModLoaded)) {
            List<String> parts = new ArrayList<>();
            a.registered().forEach((what, count) -> parts.add(what.label(count)));
            // A bundled addon such as the demo has no container of its own; it ships with HS.
            String version = FabricLoader.getInstance().isModLoaded(a.modId()) ? modVersion(a.modId()) : modVersion("historystages");
            out.add(new DebugReport.AddonLine(a.modId(), version, String.join(", ", parts), a.errors(), a.warnings()));
        }
        return out;
    }

    private static Map<String, String> versions() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("History Stages", modVersion("historystages"));
        out.put("Minecraft", modVersion("minecraft"));
        out.put("Fabric Loader", modVersion("fabricloader"));
        out.put("Fabric API", modVersion("fabric-api"));
        return out;
    }

    private static Map<String, String> compatMods() {
        Map<String, String> out = new LinkedHashMap<>();
        for (String id : COMPAT_MOD_IDS) {
            if (FabricLoader.getInstance().isModLoaded(id)) out.put(id, modVersion(id));
        }
        return out;
    }

    // Counted through the category registry rather than one accumulator per kind, so interaction
    // locks and every addon category are in it without anyone remembering to add them.
    private static Map<String, Integer> categoryTotals(Map<String, StageEntry> stages) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (LockCategory<?> category : LockCategories.all()) {
            int total = 0;
            for (StageEntry entry : stages.values()) {
                total += category.read(entry).size();
            }
            totals.put(category.id(), total);
        }
        return totals;
    }

    // Read from the specs, not listed by hand: the hand-written list had thirteen settings and
    // silently fell behind every one added after it.
    /**
     * One config file, reduced to what differs from the defaults. The full list is well over a
     * hundred lines of colours and message templates; what a pack changed is what explains a bug.
     */
    private record ConfigFile(String name, int total, Map<String, String> changed) {}

    private static List<ConfigFile> configSnapshot() {
        List<ConfigFile> out = new ArrayList<>();
        try {
            if (Config.GAMEPLAY_SPEC.isLoaded()) out.add(changedFromDefault("gameplay.toml", Config.GAMEPLAY_SPEC));
            if (Config.VISUAL_SPEC.isLoaded()) out.add(changedFromDefault("visual.toml", Config.VISUAL_SPEC));
        } catch (Exception e) {
            // A report without its config section still beats no report.
        }
        return out;
    }

    private static ConfigFile changedFromDefault(String name, net.bananemdnsa.historystages.platform.ModConfigSpec spec) {
        Map<String, String[]> all = ConfigSpecCodec.collectWithDefaults(spec);
        Map<String, String> changed = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> e : all.entrySet()) {
            if (!e.getValue()[0].equals(e.getValue()[1])) changed.put(e.getKey(), e.getValue()[0]);
        }
        return new ConfigFile(name, all.size(), changed);
    }

    private static String modVersion(String modId) {
        try {
            return FabricLoader.getInstance().getModContainer(modId)
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("unknown");
        } catch (Exception e) {
            return "unknown";
        }
    }

    // ==================== Runtime logging ====================

    private static final ConcurrentLinkedQueue<String> RUNTIME_BUFFER = new ConcurrentLinkedQueue<>();
    private static final Map<String, Long> THROTTLE_MAP = new HashMap<>();
    private static final long THROTTLE_MS = 5000;
    private static volatile String runtimeFileName = null;
    private static volatile String sessionStarted = null;
    private static volatile boolean headerWritten = false;

    public static void runtime(RuntimeCategory category, String message) {
        if (!isRuntimeEnabled()) return;
        RUNTIME_BUFFER.add(runtimeLine(LocalDateTime.now().format(TIME_FORMAT), category, message));
    }

    public static void runtime(RuntimeCategory category, String playerName, String message) {
        runtime(category, "<" + playerName + "> " + message);
    }

    public static void runtimeThrottled(RuntimeCategory category, String throttleKey, String message) {
        if (!isRuntimeEnabled()) return;
        long now = System.currentTimeMillis();
        synchronized (THROTTLE_MAP) {
            Long last = THROTTLE_MAP.get(throttleKey);
            if (last != null && (now - last) < THROTTLE_MS) return;
            THROTTLE_MAP.put(throttleKey, now);
        }
        runtime(category, message);
    }

    /** {@code [14:52:10] [LOCK  ] [Item Use Lock] <Dev> Use of 'minecraft:bow' blocked} */
    static String runtimeLine(String time, RuntimeCategory category, String message) {
        String kind = category.kind().name();
        return "[" + time + "] [" + kind + " ".repeat(6 - kind.length()) + "] [" + category.label() + "] " + message;
    }

    public static void initRuntimeSession() {
        LocalDateTime now = LocalDateTime.now();
        runtimeFileName = "runtime-" + now.format(FILE_FORMAT) + ".log";
        // Kept here, because the header is only written on the first flush — up to 30 seconds
        // later, which used to make the first lines look older than the session.
        sessionStarted = now.format(DISPLAY_FORMAT);
        headerWritten = false;

        File logsDir = logsPath().toFile();
        if (logsDir.exists()) {
            cleanupOldLogs(logsDir, "runtime-", MAX_RUNTIME_FILES);
        }
    }

    public static void flushRuntimeBuffer() {
        if (RUNTIME_BUFFER.isEmpty()) return;
        if (runtimeFileName == null) initRuntimeSession();

        try {
            File logsDir = logsPath().toFile();
            if (!logsDir.exists()) logsDir.mkdirs();

            File runtimeFile = new File(logsDir, runtimeFileName);

            try (BufferedWriter bw = new BufferedWriter(new FileWriter(runtimeFile, StandardCharsets.UTF_8, true))) {
                if (!headerWritten) {
                    headerWritten = true;
                    for (String line : List.of(
                            "================================================================",
                            "  History Stages - Runtime Log",
                            "  Session started: " + sessionStarted,
                            "  History Stages:  " + modVersion("historystages"),
                            "  Minecraft:       " + modVersion("minecraft"),
                            "  Fabric Loader:   " + modVersion("fabricloader"),
                            "",
                            "  LOCK   = a lock stopped or changed what a player did",
                            "  STAGE  = a stage was gained, lost or refused",
                            "  ADMIN  = a command or the editor changed something",
                            "  SYSTEM = the mod itself",
                            "================================================================",
                            "")) {
                        bw.write(line);
                        bw.newLine();
                    }
                }

                String entry;
                while ((entry = RUNTIME_BUFFER.poll()) != null) {
                    bw.write(entry);
                    bw.newLine();
                }
            }

        } catch (Exception e) {
            gameLog().error("Failed to flush the History Stages runtime log: {}", e.toString());
        }
    }

    public static void cleanupThrottleMap() {
        long now = System.currentTimeMillis();
        synchronized (THROTTLE_MAP) {
            THROTTLE_MAP.entrySet().removeIf(e -> (now - e.getValue()) > THROTTLE_MS * 2);
        }
    }

    private static boolean isRuntimeEnabled() {
        try {
            return Config.GAMEPLAY.enableRuntimeLogging.get();
        } catch (Throwable e) {
            // Before the config loads this is an error, not an exception, and an addon calling
            // RuntimeLog that early must not get it.
            return false;
        }
    }

    // ==================== Shared utilities ====================

    private static void cleanupOldLogs(File logsDir, String prefix, int maxFiles) {
        File[] logFiles = logsDir.listFiles((dir, name) -> name.startsWith(prefix) && name.endsWith(".log"));
        if (logFiles == null || logFiles.length < maxFiles) return;

        Arrays.sort(logFiles, Comparator.comparingLong(File::lastModified));

        int toDelete = logFiles.length - maxFiles + 1;
        for (int i = 0; i < toDelete; i++) {
            logFiles[i].delete();
        }
    }
}
