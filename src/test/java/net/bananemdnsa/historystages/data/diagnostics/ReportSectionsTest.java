package net.bananemdnsa.historystages.data.diagnostics;

import net.bananemdnsa.historystages.util.DebugReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportSectionsTest {

    @AfterEach
    void reset() {
        ReportSections.resetForTest();
        AddonRegistrations.resetForTest();
    }

    @Test
    void builtInsComeFirstAndWriteThroughTheWriter() {
        ReportSections.register("relics:sets", "Relic sets", out -> out.value("Sets in use", 4));
        ReportSections.registerBuiltIn("historystages:config", "Config", out -> out.line("[gameplay.toml] 0 of 59 changed"));

        List<DebugReport.Section> sections = ReportSections.collect();
        assertEquals("historystages:config", sections.get(0).id());
        assertTrue(sections.get(0).builtIn());
        assertEquals(List.of("[gameplay.toml] 0 of 59 changed"), sections.get(0).lines());
        assertEquals(List.of("Sets in use: 4"), sections.get(1).lines());
        assertFalse(sections.get(1).builtIn());
    }

    @Test
    void aSectionThatThrowsSaysSoAndCountsAsTheAddonsError() {
        AddonRegistrations.record("relics", "relics:broken", ApiExtension.REPORT_SECTION);
        ReportSections.register("relics:broken", "Broken", out -> { out.line("half"); throw new IllegalStateException("boom"); });
        ReportSections.register("relics:fine", "Fine", out -> out.line("ok"));

        List<DebugReport.Section> sections = ReportSections.collect();
        assertEquals(List.of("half", "(section failed: java.lang.IllegalStateException: boom)"), sections.get(0).lines());
        assertEquals(List.of("ok"), sections.get(1).lines());
        assertEquals(1, AddonRegistrations.summaries(m -> true).get(0).errors());
    }

    @Test
    void aSectionThatOverflowsTheStackIsStillJustAFailedSection() {
        ReportSections.register("relics:deep", "Deep", out -> { throw new StackOverflowError(); });
        List<DebugReport.Section> sections = assertDoesNotThrow(ReportSections::collect);
        assertEquals(List.of("(section failed: java.lang.StackOverflowError)"), sections.get(0).lines());
    }

    @Test
    void anEmptySectionIsLeftOut() {
        ReportSections.register("relics:quiet", "Quiet", out -> {});
        assertTrue(ReportSections.collect().isEmpty());
    }

    @Test
    void registrationRulesMatchTheOtherRegistries() {
        ReportSections.register("relics:a", "A", out -> {});
        assertThrows(IllegalArgumentException.class, () -> ReportSections.register("relics:a", "A", out -> {}));
        assertThrows(IllegalArgumentException.class, () -> ReportSections.register("relics:b", " ", out -> {}));
        ReportSections.freeze();
        assertThrows(IllegalStateException.class, () -> ReportSections.register("relics:c", "C", out -> {}));
    }
}
