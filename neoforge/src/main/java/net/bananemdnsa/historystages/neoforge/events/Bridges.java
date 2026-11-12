package net.bananemdnsa.historystages.neoforge.events;

import net.bananemdnsa.historystages.platform.TriState;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.bus.EventPriority;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.IEventBus;

/**
 * The pieces every bridge class shares.
 *
 * <p>Every native event gets one listener per priority. The listener at NeoForge priority X posts
 * the platform event only to the platform handlers registered at X ({@link EventBus#post(
 * net.bananemdnsa.historystages.platform.bus.Event, EventPriority)}), so a platform LOWEST handler
 * runs after other mods' NORMAL handlers, as it did when the handlers sat on NeoForge's bus
 * directly. Each priority builds a fresh platform event from the native event's current state and
 * writes back right away; the native event carries the state from one priority to the next.
 */
final class Bridges {

    /** A bridge method: the native event plus the priority whose platform handlers it should reach. */
    @FunctionalInterface
    interface PriorityListener<N> {
        void accept(N event, EventPriority priority);
    }

    private Bridges() {}

    /**
     * For a platform event that cannot be cancelled. NeoForge skips the listener once another mod
     * cancelled the native event, the same thing it would have done to the handler itself.
     */
    static <N extends Event> void listen(IEventBus bus, Class<N> type, PriorityListener<N> listener) {
        for (EventPriority priority : EventPriority.values()) {
            bus.addListener(toNative(priority), false, type, event -> listener.accept(event, priority));
        }
    }

    /**
     * For a cancellable one. The bridge hears the native event even when it arrives cancelled and
     * hands that state on, so the platform bus decides who still sees it, exactly as NeoForge
     * would: only handlers with {@code receiveCanceled}.
     */
    static <N extends Event & ICancellableEvent> void listenCancellable(IEventBus bus, Class<N> type,
                                                                      PriorityListener<N> listener) {
        for (EventPriority priority : EventPriority.values()) {
            bus.addListener(toNative(priority), true, type, event -> listener.accept(event, priority));
        }
    }

    /** Whether a platform event is worth building. Most ticks and spawns have nobody listening yet. */
    static boolean wanted(Class<? extends net.bananemdnsa.historystages.platform.bus.Event> type,
                          EventPriority priority) {
        return EventBus.hasListeners(type, priority);
    }

    /** Posts {@code platform} with the native cancel state and writes the outcome back. */
    static <P extends net.bananemdnsa.historystages.platform.bus.Event
            & net.bananemdnsa.historystages.platform.bus.ICancellableEvent> P post(ICancellableEvent nativeEvent,
                                                                                    P platform,
                                                                                    EventPriority priority) {
        carryCancel(nativeEvent, platform);
        EventBus.post(platform, priority);
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

    static net.neoforged.bus.api.EventPriority toNative(EventPriority priority) {
        return switch (priority) {
            case HIGHEST -> net.neoforged.bus.api.EventPriority.HIGHEST;
            case HIGH -> net.neoforged.bus.api.EventPriority.HIGH;
            case NORMAL -> net.neoforged.bus.api.EventPriority.NORMAL;
            case LOW -> net.neoforged.bus.api.EventPriority.LOW;
            case LOWEST -> net.neoforged.bus.api.EventPriority.LOWEST;
        };
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
