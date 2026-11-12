package net.bananemdnsa.historystages.neoforge.events;

import java.util.function.Consumer;

import net.bananemdnsa.historystages.platform.TriState;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.IEventBus;

/** The pieces every bridge class shares. */
final class Bridges {

    private Bridges() {}

    /**
     * For a platform event that cannot be cancelled. NeoForge skips the listener once another mod
     * cancelled the native event, the same thing it would have done to the handler itself.
     */
    static <N extends Event> void listen(IEventBus bus, Class<N> type, Consumer<N> listener) {
        bus.addListener(EventPriority.NORMAL, false, type, listener);
    }

    /**
     * For a cancellable one. The bridge hears the native event even when it arrives cancelled and
     * hands that state on, so the platform bus decides who still sees it, exactly as NeoForge
     * would: only handlers with {@code receiveCanceled}.
     */
    static <N extends Event & ICancellableEvent> void listenCancellable(IEventBus bus, Class<N> type,
                                                                      Consumer<N> listener) {
        bus.addListener(EventPriority.NORMAL, true, type, listener);
    }

    /** Whether a platform event is worth building. Most ticks and spawns have nobody listening yet. */
    static boolean wanted(Class<? extends net.bananemdnsa.historystages.platform.bus.Event> type) {
        return EventBus.hasListeners(type);
    }

    /** Posts {@code platform} with the native cancel state and writes the outcome back. */
    static <P extends net.bananemdnsa.historystages.platform.bus.Event
            & net.bananemdnsa.historystages.platform.bus.ICancellableEvent> P post(ICancellableEvent nativeEvent,
                                                                                    P platform) {
        carryCancel(nativeEvent, platform);
        EventBus.post(platform);
        writeCancel(nativeEvent, platform);
        return platform;
    }

    static void carryCancel(ICancellableEvent nativeEvent,
                            net.bananemdnsa.historystages.platform.bus.ICancellableEvent platform) {
        if (nativeEvent.isCanceled()) {
            platform.setCanceled(true);
        }
    }

    /** Only touches the native flag on a change: some NeoForge events reset other state in setCanceled. */
    static void writeCancel(ICancellableEvent nativeEvent,
                            net.bananemdnsa.historystages.platform.bus.ICancellableEvent platform) {
        if (platform.isCanceled() != nativeEvent.isCanceled()) {
            nativeEvent.setCanceled(platform.isCanceled());
        }
    }

    static TriState toPlatform(net.minecraft.util.TriState state) {
        return switch (state) {
            case TRUE -> TriState.TRUE;
            case FALSE -> TriState.FALSE;
            case DEFAULT -> TriState.DEFAULT;
        };
    }

    static net.minecraft.util.TriState toNative(TriState state) {
        return switch (state) {
            case TRUE -> net.minecraft.util.TriState.TRUE;
            case FALSE -> net.minecraft.util.TriState.FALSE;
            case DEFAULT -> net.minecraft.util.TriState.DEFAULT;
        };
    }
}
