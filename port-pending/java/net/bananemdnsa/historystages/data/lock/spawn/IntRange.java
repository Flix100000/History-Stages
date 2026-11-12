package net.bananemdnsa.historystages.data.lock.spawn;

/** Inclusive on both ends. A reversed pair is swapped rather than rejected. */
public record IntRange(int min, int max) {

    public IntRange {
        if (min > max) {
            int swap = min;
            min = max;
            max = swap;
        }
    }

    public boolean contains(int value) {
        return value >= min && value <= max;
    }
}
