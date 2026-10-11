package net.bananemdnsa.historystages.data.diagnostics;

import net.bananemdnsa.historystages.api.diagnostics.Problems;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.data.StageEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class StageChecksTest {

    private final List<String> reported = new ArrayList<>();
    private final StageChecks.ProblemSink sink =
            (checkId, level, category, message) -> reported.add(checkId + "|" + level + "|" + category + "|" + message);

    @AfterEach
    void reset() {
        StageChecks.resetForTest();
    }

    @Test
    void problemsCarryTheAddonNamespaceInTheirCategory() {
        StageChecks.register("relics:sets", ctx -> ctx.problems().warn("Relic Sets", "unknown set in " + ctx.stageId()));
        StageChecks.runAll("iron", StageScope.GLOBAL, new StageEntry(), sink);
        assertEquals(List.of("relics:sets|WARN|relics: Relic Sets|unknown set in iron"), reported);
    }

    @Test
    void aCheckThatThrowsBecomesAnErrorAndTheOthersStillRun() {
        StageChecks.register("relics:broken", ctx -> { throw new IllegalStateException("boom"); });
        StageChecks.register("relics:linkage", ctx -> { throw new NoClassDefFoundError("some/Optional"); });
        StageChecks.register("relics:fine", ctx -> ctx.problems().info("Relic Sets", "ok"));

        StageChecks.runAll("iron", StageScope.INDIVIDUAL, new StageEntry(), sink);

        assertEquals(3, reported.size(), reported.toString());
        assertTrue(reported.get(0).startsWith("relics:broken|ERROR|relics: Stage Check|Check 'relics:broken' failed: java.lang.IllegalStateException: boom"));
        assertTrue(reported.get(1).startsWith("relics:linkage|ERROR|"));
        assertEquals("relics:fine|INFO|relics: Relic Sets|ok", reported.get(2));
    }

    @Test
    void aCheckThatOverflowsTheStackIsStillJustAnError() {
        // Recursive addon data; left uncaught this would fail the world load.
        StageChecks.register("relics:deep", ctx -> { throw new StackOverflowError(); });
        StageChecks.register("relics:fine", ctx -> ctx.problems().info("Relic Sets", "ok"));

        assertDoesNotThrow(() -> StageChecks.runAll("iron", StageScope.GLOBAL, new StageEntry(), sink));
        assertEquals(2, reported.size(), reported.toString());
        assertTrue(reported.get(0).startsWith("relics:deep|ERROR|"));
    }

    @Test
    void theContextShowsTheStageBeingChecked() {
        StageEntry entry = new StageEntry();
        AtomicReference<String> seen = new AtomicReference<>();
        StageChecks.register("relics:look", ctx -> seen.set(ctx.stageId() + "/" + ctx.scope() + "/" + (ctx.entry() == entry)));
        StageChecks.runAll("quest", StageScope.INDIVIDUAL, entry, sink);
        assertEquals("quest/INDIVIDUAL/true", seen.get());
    }

    @Test
    void problemsKeptPastTheCheckStillReachTheSinkTheyWereHandedFor() {
        AtomicReference<Problems> kept = new AtomicReference<>();
        StageChecks.register("relics:late", ctx -> kept.set(ctx.problems()));
        StageChecks.runAll("iron", StageScope.GLOBAL, new StageEntry(), sink);

        kept.get().error("Relic Sets", "late");
        assertEquals(List.of("relics:late|ERROR|relics: Relic Sets|late"), reported);
    }

    @Test
    void registrationRulesMatchTheOtherRegistries() {
        StageChecks.register("relics:a", ctx -> {});
        assertThrows(IllegalArgumentException.class, () -> StageChecks.register("relics:a", ctx -> {}));
        assertThrows(IllegalArgumentException.class, () -> StageChecks.register("no_namespace", ctx -> {}));
        StageChecks.freeze();
        assertThrows(IllegalStateException.class, () -> StageChecks.register("relics:b", ctx -> {}));
    }
}
