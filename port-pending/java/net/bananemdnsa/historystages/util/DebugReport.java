package net.bananemdnsa.historystages.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns what {@link DebugLogger} collected into the text of the diagnostic report and the lines
 * that go into {@code latest.log}.
 *
 * <p>Plain Java on purpose. {@link DebugLogger} gathers versions, mods and config from the running
 * game; everything here only shapes text, so it can be unit-tested without Minecraft.
 */
public final class DebugReport {

    /** Notes of one kind beyond this are folded into a single "and N more" line. */
    static final int NOTES_SHOWN_PER_CATEGORY = 5;
    /** Past this many errors and warnings, latest.log only points at the report. */
    static final int LATEST_LOG_LIMIT = 50;

    static final String GENERAL = "General";
    private static final String RULE = "================================================================";

    private DebugReport() {}

    /**
     * One problem found while loading.
     *
     * @param source the stage file it belongs to, relative to {@code config/historystages/}
     *               (e.g. {@code global/ores/iron.json}), or null when it belongs to no stage
     */
    public record Issue(DebugLogger.Level level, String category, String source, String message) {}

    /**
     * Everything one report shows.
     *
     * @param versions   label → version, in display order (History Stages, Minecraft, …)
     * @param compatMods mod id → version, only the mods History Stages works with
     * @param addons     every mod that registered something through the API, HS itself excluded
     * @param sections   built-in sections first, then the addons'
     */
    public record Snapshot(String generated, Map<String, String> versions, Map<String, String> compatMods,
                           List<AddonLine> addons, int globalStages, int individualStages,
                           List<Issue> issues, List<Section> sections) {}

    /** One block of the report. Built-in ones are top-level sections, addon ones sit under ADDON SECTIONS. */
    public record Section(String id, String title, boolean builtIn, List<String> lines) {}

    /** One mod that uses the API, already summarised: {@code registered} reads "1 lock category, 2 stage checks". */
    public record AddonLine(String modId, String version, String registered, int errors, int warnings) {}

    public static List<String> render(Snapshot s) {
        List<String> out = new ArrayList<>();
        long errors = count(s.issues(), DebugLogger.Level.ERROR);
        long warnings = count(s.issues(), DebugLogger.Level.WARN);
        long notes = count(s.issues(), DebugLogger.Level.INFO);

        out.add(RULE);
        out.add("  History Stages - Diagnostic Report");
        out.add(RULE);
        out.add("  Generated:      " + s.generated());
        for (Map.Entry<String, String> v : s.versions().entrySet()) {
            out.add("  " + pad(v.getKey() + ":", 16) + v.getValue());
        }
        out.add("  " + pad("Compat mods:", 16) + (s.compatMods().isEmpty() ? "none" : joinMods(s.compatMods())));
        out.add("  " + pad("API addons:", 16) + (s.addons().isEmpty() ? "none" : addonText(s.addons().get(0))));
        for (int i = 1; i < s.addons().size(); i++) {
            out.add("  " + " ".repeat(16) + addonText(s.addons().get(i)));
        }
        out.add("");
        out.add("  Stages loaded:  " + s.globalStages() + " global, " + s.individualStages() + " individual");
        out.add("  Issues:         " + plural(errors, "error") + ", " + plural(warnings, "warning")
                + ", " + plural(notes, "note"));

        out.add("");

        section(out, "ERRORS AND WARNINGS");
        renderProblems(out, s.issues());

        section(out, "NOTES");
        renderNotes(out, s.issues());

        for (Section section : s.sections()) {
            if (!section.builtIn()) continue;
            section(out, section.title().toUpperCase(java.util.Locale.ROOT));
            for (String line : section.lines()) out.add("  " + line);
            out.add("");
        }
        List<Section> addonSections = s.sections().stream().filter(sec -> !sec.builtIn()).toList();
        if (!addonSections.isEmpty()) {
            section(out, "ADDON SECTIONS");
            for (Section section : addonSections) {
                out.add(heading(section.title() + " (" + namespace(section.id()) + ")"));
                for (String line : section.lines()) out.add("  " + line);
                out.add("");
            }
        }

        out.add(RULE);
        out.add("  Written on every server start by History Stages. Attach it to bug reports,");
        out.add("  together with the stage files named above.");
        out.add("  Stage files: config/historystages/global/ and config/historystages/individual/");
        out.add(RULE);
        return out;
    }

    /**
     * What goes into latest.log: one summary line naming the report, then every error and
     * warning with its file. Notes stay in the report.
     */
    public static List<LogLine> latestLogLines(List<Issue> issues, String reportPath) {
        List<LogLine> out = new ArrayList<>();
        long errors = count(issues, DebugLogger.Level.ERROR);
        long warnings = count(issues, DebugLogger.Level.WARN);

        if (errors == 0 && warnings == 0) {
            out.add(new LogLine(DebugLogger.Level.INFO,
                    "Stages loaded without errors or warnings. Report: " + reportPath));
            return out;
        }
        DebugLogger.Level summaryLevel = errors > 0 ? DebugLogger.Level.ERROR : DebugLogger.Level.WARN;
        out.add(new LogLine(summaryLevel, "Found " + plural(errors, "error") + " and "
                + plural(warnings, "warning") + " in the stage files. Full report: " + reportPath));

        int shown = 0;
        for (Issue issue : sortedProblems(issues)) {
            if (shown == LATEST_LOG_LIMIT) {
                out.add(new LogLine(summaryLevel, "... and " + (errors + warnings - shown)
                        + " more, see the report."));
                break;
            }
            String where = issue.source() != null ? issue.source() + ": " : "";
            out.add(new LogLine(issue.level(), where + issue.message()));
            shown++;
        }
        return out;
    }

    public record LogLine(DebugLogger.Level level, String message) {}

    // ---- sections ------------------------------------------------------------------------

    /** Errors and warnings, one block per stage file, errors first inside each block. */
    private static void renderProblems(List<String> out, List<Issue> issues) {
        Map<String, List<Issue>> bySource = new LinkedHashMap<>();
        for (Issue issue : sortedProblems(issues)) {
            bySource.computeIfAbsent(sourceLabel(issue), k -> new ArrayList<>()).add(issue);
        }
        if (bySource.isEmpty()) {
            out.add("  None.");
            out.add("");
            return;
        }
        for (Map.Entry<String, List<Issue>> block : bySource.entrySet()) {
            out.add(heading(block.getKey()));
            for (Issue issue : block.getValue()) {
                out.add("  " + tag(issue.level()) + issue.category() + ": " + issue.message());
            }
            out.add("");
        }
    }

    /**
     * Notes grouped by kind. A pack with forty empty test stages would otherwise bury the one
     * note that matters, so each kind shows a few in full and only names the rest.
     */
    private static void renderNotes(List<String> out, List<Issue> issues) {
        Map<String, List<Issue>> byCategory = new LinkedHashMap<>();
        for (Issue issue : issues) {
            if (issue.level() != DebugLogger.Level.INFO) continue;
            byCategory.computeIfAbsent(issue.category(), k -> new ArrayList<>()).add(issue);
        }
        if (byCategory.isEmpty()) {
            out.add("  None.");
            out.add("");
            return;
        }
        for (Map.Entry<String, List<Issue>> block : byCategory.entrySet()) {
            List<Issue> notes = block.getValue();
            out.add(heading(block.getKey() + " (" + notes.size() + ")"));
            for (int i = 0; i < Math.min(notes.size(), NOTES_SHOWN_PER_CATEGORY); i++) {
                Issue note = notes.get(i);
                out.add("  " + (note.source() != null ? "[" + note.source() + "] " : "") + note.message());
            }
            if (notes.size() > NOTES_SHOWN_PER_CATEGORY) {
                List<Issue> rest = notes.subList(NOTES_SHOWN_PER_CATEGORY, notes.size());
                List<String> sources = rest.stream().map(Issue::source).filter(src -> src != null)
                        .distinct().toList();
                String more = "  ... and " + rest.size() + " more";
                out.add(sources.isEmpty() ? more : more + ": " + String.join(", ", sources));
            }
            out.add("");
        }
    }

    // ---- helpers -------------------------------------------------------------------------

    /** Errors and warnings only: by file (general last), then errors before warnings. */
    private static List<Issue> sortedProblems(List<Issue> issues) {
        // Stable sort, so issues keep the order they were found in within a file and level.
        return issues.stream()
                .filter(i -> i.level() != DebugLogger.Level.INFO)
                .sorted(Comparator.comparing((Issue i) -> i.source() == null)
                        .thenComparing(i -> i.source() == null ? "" : i.source())
                        .thenComparing(Issue::level))
                .toList();
    }

    private static String sourceLabel(Issue issue) {
        return issue.source() != null ? issue.source() : GENERAL;
    }

    private static String tag(DebugLogger.Level level) {
        return switch (level) {
            case ERROR -> "[ERROR] ";
            case WARN -> "[WARN]  ";
            case INFO -> "[INFO]  ";
        };
    }

    private static void section(List<String> out, String title) {
        out.add(RULE);
        out.add("  " + title);
        out.add(RULE);
        out.add("");
    }

    private static String heading(String title) {
        return "--- " + title + " " + "-".repeat(Math.max(3, 60 - title.length() - 5));
    }

    private static String addonText(AddonLine a) {
        String text = a.modId() + " " + a.version() + " - " + a.registered();
        if (a.errors() > 0 || a.warnings() > 0) {
            List<String> parts = new ArrayList<>();
            if (a.errors() > 0) parts.add(plural(a.errors(), "error"));
            if (a.warnings() > 0) parts.add(plural(a.warnings(), "warning"));
            text += " (" + String.join(", ", parts) + ")";
        }
        return text;
    }

    private static String namespace(String id) {
        int colon = id.indexOf(':');
        return colon > 0 ? id.substring(0, colon) : id;
    }

    private static String joinMods(Map<String, String> mods) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> mod : new TreeMap<>(mods).entrySet()) {
            parts.add(mod.getKey() + " " + mod.getValue());
        }
        return String.join(", ", parts);
    }

    private static long count(List<Issue> issues, DebugLogger.Level level) {
        return issues.stream().filter(i -> i.level() == level).count();
    }

    private static String plural(long n, String word) {
        return n + " " + word + (n == 1 ? "" : "s");
    }

    private static String pad(String s, int width) {
        return s.length() >= width ? s + " " : s + " ".repeat(width - s.length());
    }
}
