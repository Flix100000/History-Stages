package net.bananemdnsa.historystages.data.graph;

import java.util.Collection;
import java.util.Comparator;

/**
 * Picks whose background the player's map shows: the most recently unlocked stage that has one.
 *
 * <p>Stages unlocked before this world recorded unlock times have none. They count as older than
 * anything that does, and among themselves the deepest stage in the tree wins — the closest guess
 * at "latest" there is without a clock. Depth also settles two unlocks in the same tick, which
 * the pedestal produces, and the key settles the rest so the result never flickers between two.
 */
public final class UnlockBackgroundResolver {

    /**
     * @param key        graph key ({@code g:id} / {@code i:id}), so a global and an individual
     *                   stage sharing an id stay two candidates
     * @param unlockTime game time of the unlock, or null when it predates recorded times
     */
    public record Candidate(String key, Long unlockTime, int depth, CanvasBackgroundStyle background) {}

    private static final Comparator<Candidate> NEWEST_FIRST = Comparator
            .comparingLong((Candidate c) -> c.unlockTime() == null ? Long.MIN_VALUE : c.unlockTime())
            .thenComparingInt(Candidate::depth)
            .reversed()
            .thenComparing(Candidate::key);

    private UnlockBackgroundResolver() {}

    /** @return the winning stage's block, or null when no unlocked stage sets a background */
    public static CanvasBackgroundStyle pick(Collection<Candidate> candidates) {
        return candidates.stream()
                .filter(c -> c.background() != null && !c.background().isEmpty())
                .min(NEWEST_FIRST)
                .map(Candidate::background)
                .orElse(null);
    }
}
