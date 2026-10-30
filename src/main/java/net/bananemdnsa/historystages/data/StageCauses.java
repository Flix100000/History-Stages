package net.bananemdnsa.historystages.data;

import java.util.function.Supplier;

import net.bananemdnsa.historystages.api.stage.StageChangeCause;

/**
 * The cause in force on this thread, read by every {@code StageEvent} when it is created.
 *
 * <p>A context rather than a parameter: the events are built in a dozen places, several of them
 * outside {@code StageStates}, and threading a cause through every signature between a trigger
 * and the event would touch most of the mod. Each path that changes stages wraps itself once;
 * everything posted inside picks the cause up, and an inner path — team sharing reacting to a
 * research unlock — wraps itself again and wins for its own events.
 */
public final class StageCauses {

    private static final ThreadLocal<StageChangeCause> CURRENT = new ThreadLocal<>();

    private StageCauses() {}

    /** The cause in force, or {@link StageChangeCause#ADDON} when no internal path set one. */
    public static StageChangeCause current() {
        StageChangeCause cause = CURRENT.get();
        return cause != null ? cause : StageChangeCause.ADDON;
    }

    public static <T> T with(StageChangeCause cause, Supplier<T> action) {
        StageChangeCause previous = CURRENT.get();
        CURRENT.set(cause);
        try {
            return action.get();
        } finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }

    public static void run(StageChangeCause cause, Runnable action) {
        with(cause, () -> {
            action.run();
            return null;
        });
    }
}
