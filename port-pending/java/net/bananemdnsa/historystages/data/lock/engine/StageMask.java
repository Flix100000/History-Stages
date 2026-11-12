package net.bananemdnsa.historystages.data.lock.engine;

import java.util.Collection;

/**
 * A set of stages as bits, so "is the player missing any of these?" is one pass over a few longs
 * instead of one hash lookup per stage.
 *
 * <p>Worth having only where a subject is gated by many stages at once. That is not the ordinary
 * case — most things are gated by one — but it is exactly what a pack does when it tiers by mod:
 * twenty stages each locking the same mod makes every item of that mod depend on all twenty.
 * Measured at that shape, asking the player's set twenty times costs 364ns against roughly five
 * for a mask.
 *
 * <p>Immutable, and cheap to keep: three hundred stages fit in five longs.
 */
public final class StageMask {

    /** Nothing set. */
    public static final StageMask EMPTY = new StageMask(new long[0]);

    private final long[] words;

    private StageMask(long[] words) {
        this.words = words;
    }

    /**
     * The stages this collection names, as bits.
     *
     * <p>A stage the index does not know is skipped rather than rejected: a mask is built from
     * whatever set the caller holds, and a client that has not yet received a freshly created
     * stage should read it as "not unlocked", not throw.
     */
    public static StageMask of(StageIndex index, Collection<String> stageIds) {
        if (stageIds.isEmpty() || index.size() == 0) return EMPTY;
        long[] words = new long[(index.size() + 63) >>> 6];
        for (String stageId : stageIds) {
            int number = index.numberOf(stageId);
            if (number < 0) continue;
            words[number >>> 6] |= 1L << (number & 63);
        }
        return new StageMask(words);
    }

    /** Whether this mask has the given stage's bit set. */
    public boolean contains(StageIndex index, String stageId) {
        int number = index.numberOf(stageId);
        if (number < 0) return false;
        int word = number >>> 6;
        return word < words.length && (words[word] & (1L << (number & 63))) != 0;
    }

    /**
     * Whether any stage in {@code required} is missing from this mask — the lock question, in one
     * pass. Equivalent to {@code LockResolution.isLocked} over the same stages.
     */
    public boolean missesAnyOf(StageMask required) {
        for (int i = 0; i < required.words.length; i++) {
            long needed = required.words[i];
            if (needed == 0) continue;
            long held = i < words.length ? words[i] : 0L;
            if ((needed & ~held) != 0) return true;
        }
        return false;
    }

    /** Whether this mask shares at least one bit with {@code other}. */
    public boolean hasAnyOf(StageMask other) {
        int n = Math.min(words.length, other.words.length);
        for (int i = 0; i < n; i++) {
            if ((words[i] & other.words[i]) != 0) return true;
        }
        return false;
    }

    /** The bits set in both masks. */
    public StageMask and(StageMask other) {
        int n = Math.min(words.length, other.words.length);
        long[] out = new long[n];
        boolean any = false;
        for (int i = 0; i < n; i++) {
            out[i] = words[i] & other.words[i];
            any |= out[i] != 0;
        }
        return any ? new StageMask(out) : EMPTY;
    }

    /** The bits set here and not in {@code other}. */
    public StageMask andNot(StageMask other) {
        long[] out = new long[words.length];
        boolean any = false;
        for (int i = 0; i < words.length; i++) {
            long removed = i < other.words.length ? other.words[i] : 0L;
            out[i] = words[i] & ~removed;
            any |= out[i] != 0;
        }
        return any ? new StageMask(out) : EMPTY;
    }

    /** True when no bit is set. */
    public boolean isEmpty() {
        for (long word : words) {
            if (word != 0) return false;
        }
        return true;
    }
}
