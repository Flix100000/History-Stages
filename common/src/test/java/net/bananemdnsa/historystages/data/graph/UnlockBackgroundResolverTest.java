package net.bananemdnsa.historystages.data.graph;

import net.bananemdnsa.historystages.data.graph.UnlockBackgroundResolver.Candidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UnlockBackgroundResolverTest {

    /** A candidate whose background texture is its own name, so the winner is easy to read off. */
    private static Candidate c(String key, Long time, int depth, boolean hasBackground) {
        CanvasBackgroundStyle bg = null;
        if (hasBackground) {
            bg = new CanvasBackgroundStyle();
            bg.texture = key;
        }
        return new Candidate(key, time, depth, bg);
    }

    private static String winner(Candidate... candidates) {
        CanvasBackgroundStyle picked = UnlockBackgroundResolver.pick(List.of(candidates));
        return picked == null ? null : picked.texture;
    }

    @Test
    void theLatestUnlockWins() {
        assertEquals("g:b", winner(c("g:a", 10L, 5, true), c("g:b", 20L, 0, true)));
    }

    @Test
    void aTimedUnlockBeatsAnUntimedOne() {
        // Untimed means "unlocked before this world recorded times", so even time 0 is newer.
        assertEquals("g:new", winner(c("g:old", null, 9, true), c("g:new", 0L, 0, true)));
    }

    @Test
    void untimedUnlocksFallBackToDepth() {
        assertEquals("g:deep", winner(c("g:flat", null, 1, true), c("g:deep", null, 4, true)));
    }

    @Test
    void aTieOnTimeGoesToDepthThenKey() {
        assertEquals("g:deep", winner(c("g:a", 5L, 1, true), c("g:deep", 5L, 2, true)));
        assertEquals("g:a", winner(c("g:b", 5L, 1, true), c("g:a", 5L, 1, true)));
    }

    @Test
    void stagesWithoutABackgroundAreIgnored() {
        assertEquals("g:a", winner(c("g:a", 1L, 0, true), c("g:b", 9L, 0, false)));
        Candidate emptyBlock = new Candidate("g:c", 99L, 0, new CanvasBackgroundStyle());
        assertEquals("g:a", winner(c("g:a", 1L, 0, true), emptyBlock));
    }

    @Test
    void globalAndIndividualWithTheSameIdAreSeparateCandidates() {
        assertEquals("i:x", winner(c("g:x", 3L, 0, true), c("i:x", 4L, 0, true)));
    }

    @Test
    void nothingUnlockedMeansNoOverride() {
        assertNull(UnlockBackgroundResolver.pick(List.of()));
    }
}
