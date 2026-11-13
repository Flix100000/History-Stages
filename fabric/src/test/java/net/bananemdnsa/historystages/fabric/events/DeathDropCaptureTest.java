package net.bananemdnsa.historystages.fabric.events;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A death inside a death (a drop that kills, a loot function that hurts) must not take the outer
 * death's drops with it. Entities are null here: the capture only counts what it holds.
 */
class DeathDropCaptureTest {

    @Test
    void aNestedCaptureKeepsTheOuterOnesDrops() {
        DeathDropCapture.begin(null);
        DeathDropCapture.capture(null);
        DeathDropCapture.capture(null);

        DeathDropCapture.begin(null);
        DeathDropCapture.capture(null);
        assertEquals(1, DeathDropCapture.end().size(), "inner death");

        assertEquals(2, DeathDropCapture.end().size(), "outer death lost its drops to the inner one");
        assertFalse(DeathDropCapture.isActive());
    }
}
