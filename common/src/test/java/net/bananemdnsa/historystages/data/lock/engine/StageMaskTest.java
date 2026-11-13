package net.bananemdnsa.historystages.data.lock.engine;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageMaskTest {

    // 70 stages, so the masks span two words and the second-word paths get exercised.
    private static final List<String> IDS = java.util.stream.IntStream.range(0, 70)
            .mapToObj(i -> "s" + i).toList();
    private static final StageIndex INDEX = StageIndex.of(IDS, List.of());

    private static StageMask mask(String... ids) {
        return StageMask.of(INDEX, List.of(ids));
    }

    @Test
    void hasAnyOfFindsASharedBitInEitherWord() {
        assertTrue(mask("s1", "s69").hasAnyOf(mask("s69")));
        assertFalse(mask("s1").hasAnyOf(mask("s2", "s69")));
        assertFalse(StageMask.EMPTY.hasAnyOf(mask("s1")));
    }

    @Test
    void andKeepsOnlySharedBits() {
        StageMask both = mask("s1", "s2", "s69").and(mask("s2", "s69", "s3"));
        assertTrue(both.contains(INDEX, "s2"));
        assertTrue(both.contains(INDEX, "s69"));
        assertFalse(both.contains(INDEX, "s1"));
        assertTrue(mask("s1").and(mask("s2")).isEmpty());
    }

    @Test
    void andNotRemovesTheOtherMasksBits() {
        StageMask rest = mask("s1", "s2", "s69").andNot(mask("s2"));
        assertTrue(rest.contains(INDEX, "s1"));
        assertTrue(rest.contains(INDEX, "s69"));
        assertFalse(rest.contains(INDEX, "s2"));
        assertTrue(mask("s1").andNot(mask("s1")).isEmpty());
    }
}
