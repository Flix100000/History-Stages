package net.bananemdnsa.historystages.fabric.events;

import java.util.function.Supplier;

import net.bananemdnsa.historystages.platform.TriState;
import org.jspecify.annotations.Nullable;

/**
 * What the platform handlers decided about the right click in progress: may the block react, may
 * the held item be used.
 *
 * <p>The event is raised from Fabric's {@code UseBlockCallback}, one listener per priority, but the
 * two answers are acted on later, where vanilla calls the block and the item ({@code
 * ServerUseItemOnMixin}, {@code ClientUseItemOnMixin}). This carries them across. One slot per
 * thread, because the client and the integrated server click on different threads; the mixins open
 * and close it around each {@code useItemOn} call, so nothing from an earlier click can leak in.
 */
public final class RightClickBlockState {

    private static final ThreadLocal<RightClickBlockState> CURRENT = new ThreadLocal<>();

    TriState useBlock = TriState.DEFAULT;
    TriState useItem = TriState.DEFAULT;

    private RightClickBlockState() {}

    /** Runs one {@code useItemOn} call with a fresh state, restoring whatever was open around it. */
    public static <T> T around(Supplier<T> call) {
        RightClickBlockState outer = CURRENT.get();
        CURRENT.set(new RightClickBlockState());
        try {
            return call.get();
        } finally {
            CURRENT.set(outer);
        }
    }

    /** The state of the click in progress, or null when the callback fires outside {@code useItemOn}. */
    static @Nullable RightClickBlockState current() {
        return CURRENT.get();
    }

    public static boolean blockRefused() {
        RightClickBlockState state = CURRENT.get();
        return state != null && state.useBlock == TriState.FALSE;
    }

    public static boolean itemRefused() {
        RightClickBlockState state = CURRENT.get();
        return state != null && state.useItem == TriState.FALSE;
    }
}
