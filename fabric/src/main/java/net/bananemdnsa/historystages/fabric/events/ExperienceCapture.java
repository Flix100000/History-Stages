package net.bananemdnsa.historystages.fabric.events;

/**
 * Counts the experience a block would pop instead of spawning it. Vanilla has no "how much
 * experience" question, only {@code spawnAfterBreak}, which drops it straight into the world.
 *
 * <p>A quiet capture also keeps the block's other after-break work out of the world (the
 * silverfish of infested stone): it is only asking what the block would give, nothing was broken.
 */
public final class ExperienceCapture {

    private record Scope(int[] sum, boolean quiet) {}

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private ExperienceCapture() {}

    /** Runs {@code body} and returns the experience it tried to pop; none of it spawns. */
    public static int capture(Runnable body, boolean quiet) {
        Scope previous = CURRENT.get();
        Scope scope = new Scope(new int[1], quiet);
        CURRENT.set(scope);
        try {
            body.run();
        } finally {
            CURRENT.set(previous);
        }
        return scope.sum()[0];
    }

    /** True when an experience pop was taken over by a running capture. */
    public static boolean swallow(int amount) {
        Scope scope = CURRENT.get();
        if (scope == null) return false;
        scope.sum()[0] += amount;
        return true;
    }

    /** True while a quiet capture runs: entities its block tries to add stay out of the world. */
    public static boolean quiet() {
        Scope scope = CURRENT.get();
        return scope != null && scope.quiet();
    }
}
