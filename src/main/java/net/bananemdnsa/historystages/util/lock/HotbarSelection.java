package net.bananemdnsa.historystages.util.lock;

import java.util.function.IntPredicate;

/** Which hotbar slot the selection may land on when some are locked. Pure, for testing. */
public final class HotbarSelection {

    private static final int SIZE = 9;

    private HotbarSelection() {}

    /** One scroll step from {@code current}; skips locked slots, wraps, and stays put if all are locked. */
    public static int next(int current, int direction, IntPredicate locked) {
        int step = direction > 0 ? 1 : -1;
        for (int i = 1; i < SIZE; i++) {
            int candidate = Math.floorMod(current + step * i, SIZE);
            if (!locked.test(candidate)) return candidate;
        }
        return current;
    }

    /** A direct pick (number key, packet): taken only when that slot is open. */
    public static int accept(int current, int requested, IntPredicate locked) {
        return locked.test(requested) ? current : requested;
    }

    /** If the current slot itself is locked, the next open one to the right; otherwise unchanged. */
    public static int settle(int current, IntPredicate locked) {
        return locked.test(current) ? next(current, 1, locked) : current;
    }
}
