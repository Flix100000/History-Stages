package net.bananemdnsa.historystages.client.slotlock;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewGuardTest {

    @Test
    void throwingFactoryGivesNothingAndIsRemembered() {
        PreviewGuard guard = new PreviewGuard();
        assertTrue(guard.attempt("mod:machine", () -> { throw new IllegalStateException("needs a level"); }).isEmpty());
        assertTrue(guard.hasFailed("mod:machine"));
    }

    @Test
    void secondAttemptForFailedTargetSkipsFactory() {
        PreviewGuard guard = new PreviewGuard();
        AtomicInteger calls = new AtomicInteger();
        guard.attempt("mod:machine", () -> { calls.incrementAndGet(); throw new RuntimeException(); });
        guard.attempt("mod:machine", () -> { calls.incrementAndGet(); return "menu"; });
        assertEquals(1, calls.get());
    }

    @Test
    void nullResultCountsAsFailure() {
        PreviewGuard guard = new PreviewGuard();
        assertTrue(guard.attempt("mod:machine", () -> null).isEmpty());
        assertTrue(guard.hasFailed("mod:machine"));
    }

    @Test
    void workingFactoryIsUsed() {
        PreviewGuard guard = new PreviewGuard();
        assertEquals("menu", guard.attempt("minecraft:chest", () -> "menu").orElseThrow());
        assertFalse(guard.hasFailed("minecraft:chest"));
    }

    /** A screen that builds but throws on first draw is marked by the caller and stays marked. */
    @Test
    void renderFailureCanBeMarkedAfterwards() {
        PreviewGuard guard = new PreviewGuard();
        guard.attempt("mod:machine", () -> "menu");
        guard.markFailed("mod:machine");
        assertTrue(guard.attempt("mod:machine", () -> "menu").isEmpty());
    }
}
