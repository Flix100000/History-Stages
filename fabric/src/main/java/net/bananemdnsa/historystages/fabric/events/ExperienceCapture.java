package net.bananemdnsa.historystages.fabric.events;

/**
 * Counts the experience a block would pop instead of spawning it. Vanilla has no "how much
 * experience" question, only {@code spawnAfterBreak}, which drops it straight into the world.
 */
public final class ExperienceCapture {

    private static final ThreadLocal<int[]> CURRENT = new ThreadLocal<>();

    private ExperienceCapture() {}

    /** Runs {@code body} and returns the experience it tried to pop; none of it spawns. */
    public static int capture(Runnable body) {
        int[] previous = CURRENT.get();
        int[] sum = new int[1];
        CURRENT.set(sum);
        try {
            body.run();
        } finally {
            CURRENT.set(previous);
        }
        return sum[0];
    }

    /** True when an experience pop was taken over by a running capture. */
    public static boolean swallow(int amount) {
        int[] sum = CURRENT.get();
        if (sum == null) return false;
        sum[0] += amount;
        return true;
    }
}
