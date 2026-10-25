package net.bananemdnsa.historystages.util;

import net.bananemdnsa.historystages.api.diagnostics.Kind;
import net.bananemdnsa.historystages.api.diagnostics.RuntimeCategory;
import net.bananemdnsa.historystages.util.DebugLogger.Level;
import net.bananemdnsa.historystages.util.DebugReport.Issue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The shape of the diagnostic report: problems under the file they belong to, errors before
 * warnings, notes folded, and the latest.log lines pointing at the report.
 */
class DebugReportTest {

    private static DebugReport.Snapshot snapshot(List<Issue> issues) {
        Map<String, String> versions = new LinkedHashMap<>();
        versions.put("History Stages", "6.1.0");
        versions.put("Minecraft", "1.21.1");
        List<DebugReport.Section> sections = List.of(
                new DebugReport.Section("historystages:lock_entries", "Lock entries across global stages", true,
                        List.of("historystages:items: 3")),
                new DebugReport.Section("historystages:config", "Config (changed from default)", true,
                        List.of("[gameplay.toml] 1 of 40 changed", "  logging.showDebugErrors = false")),
                new DebugReport.Section("hsdemo:relics", "Relic sets", false, List.of("Sets in use: 4")));
        List<DebugReport.AddonLine> addons = List.of(
                new DebugReport.AddonLine("hsdemo", "6.1.0", "1 lock category, 1 stage check", 0, 2));
        return new DebugReport.Snapshot("2026-10-11 12:00:00", versions, Map.of("lootr", "1.0"), addons,
                2, 1, issues, sections);
    }

    private static int indexOf(List<String> lines, String needle) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(needle)) return i;
        }
        return -1;
    }

    @Test
    void problemsAreGroupedUnderTheirFileWithErrorsFirst() {
        List<String> out = DebugReport.render(snapshot(List.of(
                new Issue(Level.WARN, "Unknown Items", "global/b.json", "warn-b"),
                new Issue(Level.WARN, "Re-lock", null, "warn-general"),
                new Issue(Level.ERROR, "Stage Loading", "global/b.json", "error-b"),
                new Issue(Level.WARN, "Unknown Items", "global/a.json", "warn-a"))));

        int a = indexOf(out, "--- global/a.json");
        int b = indexOf(out, "--- global/b.json");
        int general = indexOf(out, "--- " + DebugReport.GENERAL);
        assertTrue(a >= 0 && a < b && b < general, "files sorted, general last: " + out);

        // Inside b.json the error comes before the warning, though it was found after it.
        assertTrue(indexOf(out, "error-b") < indexOf(out, "warn-b"));
        assertTrue(out.get(indexOf(out, "error-b")).contains("[ERROR] Stage Loading: error-b"));
    }

    @Test
    void notesStayOutOfTheProblemSectionAndAreFolded() {
        List<Issue> issues = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            issues.add(new Issue(Level.INFO, "Empty Stages", "global/s" + i + ".json", "empty " + i));
        }
        List<String> out = DebugReport.render(snapshot(issues));

        int problems = indexOf(out, "ERRORS AND WARNINGS");
        int notes = indexOf(out, "  NOTES");
        assertEquals("  None.", out.get(problems + 3), "no warnings, so the section says so");
        assertTrue(indexOf(out, "--- Empty Stages (8)") > notes);

        assertTrue(indexOf(out, "empty 4") >= 0);
        assertEquals(-1, indexOf(out, "empty 5"), "only the first five are spelled out");
        assertTrue(out.contains("  ... and 3 more: global/s5.json, global/s6.json, global/s7.json"), out.toString());
    }

    @Test
    void headerListsVersionsCompatModsAndApiAddons() {
        List<String> out = DebugReport.render(snapshot(List.of()));

        assertTrue(out.get(indexOf(out, "Minecraft:")).endsWith("1.21.1"));
        assertTrue(out.get(indexOf(out, "Compat mods:")).endsWith("lootr 1.0"));
        assertTrue(out.get(indexOf(out, "API addons:")).endsWith("hsdemo 6.1.0 - 1 lock category, 1 stage check (2 warnings)"), out.toString());
    }

    @Test
    void builtInSectionsAreTopLevelAndAddonSectionsAreGroupedBelow() {
        List<String> out = DebugReport.render(snapshot(List.of()));

        int notes = indexOf(out, "  NOTES");
        int locks = indexOf(out, "  LOCK ENTRIES ACROSS GLOBAL STAGES");
        int config = indexOf(out, "  CONFIG (CHANGED FROM DEFAULT)");
        int addons = indexOf(out, "  ADDON SECTIONS");
        int relic = indexOf(out, "--- Relic sets (hsdemo)");
        assertTrue(notes < locks && locks < config && config < addons && addons < relic, out.toString());
        assertEquals("  Sets in use: 4", out.get(relic + 1));
        assertEquals("    logging.showDebugErrors = false", out.get(config + 4));
    }

    @Test
    void noAddonsMeansNoAddonSectionsHeading() {
        DebugReport.Snapshot s = snapshot(List.of());
        DebugReport.Snapshot bare = new DebugReport.Snapshot(s.generated(), s.versions(), s.compatMods(), List.of(),
                s.globalStages(), s.individualStages(), s.issues(), s.sections().subList(0, 2));
        List<String> out = DebugReport.render(bare);
        assertEquals(-1, indexOf(out, "ADDON SECTIONS"));
        assertTrue(out.get(indexOf(out, "API addons:")).endsWith("none"));
    }

    @Test
    void latestLogGetsASummaryAndEveryProblemButNoNotes() {
        List<DebugReport.LogLine> lines = DebugReport.latestLogLines(List.of(
                new Issue(Level.WARN, "Unknown Items", "global/a.json", "warn-a"),
                new Issue(Level.INFO, "Empty Stages", "global/b.json", "note-b"),
                new Issue(Level.ERROR, "Disguise", null, "error-general")), "logs/debug-x.log");

        assertEquals(3, lines.size(), lines.toString());
        assertEquals(Level.ERROR, lines.get(0).level());
        assertTrue(lines.get(0).message().contains("1 error and 1 warning"));
        assertTrue(lines.get(0).message().endsWith("logs/debug-x.log"));
        assertEquals("global/a.json: warn-a", lines.get(1).message());
        assertEquals("error-general", lines.get(2).message());
    }

    @Test
    void latestLogStopsAfterTheLimit() {
        List<Issue> issues = new ArrayList<>();
        for (int i = 0; i < DebugReport.LATEST_LOG_LIMIT + 7; i++) {
            issues.add(new Issue(Level.WARN, "X", null, "w" + i));
        }
        List<DebugReport.LogLine> lines = DebugReport.latestLogLines(issues, "r.log");

        // Summary, the limit, and one "and N more".
        assertEquals(DebugReport.LATEST_LOG_LIMIT + 2, lines.size());
        assertEquals("... and 7 more, see the report.", lines.get(lines.size() - 1).message());
    }

    @Test
    void aCleanLoadStillSaysWhereTheReportIs() {
        List<DebugReport.LogLine> lines = DebugReport.latestLogLines(
                List.of(new Issue(Level.INFO, "Empty Stages", null, "n")), "r.log");

        assertEquals(1, lines.size());
        assertEquals(Level.INFO, lines.get(0).level());
        assertTrue(lines.get(0).message().endsWith("r.log"));
    }

    @Test
    void issuesLoggedInsideASourceAreStampedWithIt() {
        DebugLogger.clear();
        try {
            DebugLogger.warn("Before", "outside");
            DebugLogger.beginSource("individual/quests/q.json");
            DebugLogger.warn("Inside", "inside");
            DebugLogger.endSource();
            DebugLogger.warn("After", "outside again");

            List<String> out = DebugReport.render(snapshot(DebugLogger.issuesForTest()));
            int file = indexOf(out, "--- individual/quests/q.json");
            int general = indexOf(out, "--- " + DebugReport.GENERAL);
            assertTrue(file >= 0 && indexOf(out, "Inside: inside") == file + 1, out.toString());
            assertTrue(indexOf(out, "outside again") > general && indexOf(out, "Before: outside") > general);
        } finally {
            DebugLogger.clear();
        }
    }

    @Test
    void runtimeLinesCarryTheirKindInAFixedWidthColumn() {
        assertEquals("[12:00:00] [LOCK  ] [Item Use Lock] <Dev> x",
                DebugLogger.runtimeLine("12:00:00", HsRuntimeCategories.ITEM_USE_LOCK, "<Dev> x"));
        assertEquals("[12:00:00] [SYSTEM] [Server] y",
                DebugLogger.runtimeLine("12:00:00", HsRuntimeCategories.SERVER, "y"));
        assertEquals("[12:00:00] [LOCK  ] [Relic Lock] z", DebugLogger.runtimeLine("12:00:00",
                RuntimeCategory.of("hsdemo:relic_lock", "Relic Lock", Kind.LOCK), "z"));
    }
}
