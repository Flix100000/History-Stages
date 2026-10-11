package net.bananemdnsa.historystages.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DebugLoggerPassTest {

    @AfterEach
    void reset() {
        DebugLogger.clear();
    }

    @Test
    void aSecondRegistryPassReplacesTheFirstInsteadOfAddingToIt() {
        DebugLogger.clear();
        DebugLogger.warn("Load", "from loading");

        DebugLogger.beginRegistryPass();
        DebugLogger.warn("Unknown Items", "pass one");
        DebugLogger.endRegistryPass();
        DebugLogger.warn("Recipe Locks", "between passes");

        DebugLogger.beginRegistryPass();
        DebugLogger.warn("Unknown Items", "pass two");
        DebugLogger.endRegistryPass();

        List<String> messages = DebugLogger.issuesForTest().stream().map(DebugReport.Issue::message).toList();
        assertEquals(List.of("from loading", "between passes", "pass two"), messages);
    }

    @Test
    void anExplicitSourceWinsOverTheCurrentOne() {
        DebugLogger.clear();
        DebugLogger.beginSource("global/a.json");
        DebugLogger.log("X", DebugLogger.Level.WARN, "m", "global/b.json");
        DebugLogger.endSource();
        assertEquals("global/b.json", DebugLogger.issuesForTest().get(0).source());
    }
}
