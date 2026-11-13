package net.bananemdnsa.historystages.fabric.events;

import java.util.function.Function;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.bus.EventPriority;
import net.bananemdnsa.historystages.platform.bus.ICancellableEvent;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.Identifier;

/**
 * Maps platform priorities onto Fabric callback phases.
 *
 * <p>Every bridged callback gets one listener per priority: HIGHEST and HIGH in phases ordered
 * before Fabric's default phase, NORMAL in the default phase itself (so it sits among other mods'
 * plain registrations in registration order, as on NeoForge), LOW and LOWEST in phases after it.
 * The listener for priority X posts only to the platform handlers registered at X, so a LOWEST
 * handler runs after other mods' callbacks.
 *
 * <p>The limit is Fabric's: most callbacks stop at the first answer other than PASS. When another
 * mod refuses, the later phases never run, so a LOWEST handler with {@code receiveCanceled} does not
 * hear about it the way it would on NeoForge. When one of ours cancels, {@link #finishCancelled}
 * hands the cancelled event to the remaining platform priorities before the chain ends, so our own
 * receiveCanceled handlers still see it.
 */
public final class Phases {

    private static final EventPriority[] PRIORITIES = EventPriority.values();

    private static final Identifier HIGHEST = id("highest");
    private static final Identifier HIGH = id("high");
    private static final Identifier LOW = id("low");
    private static final Identifier LOWEST = id("lowest");

    private Phases() {}

    /** Registers {@code factory.apply(priority)} once per priority in the matching phase. */
    public static <T> void listen(Event<T> event, Function<EventPriority, T> factory) {
        event.addPhaseOrdering(HIGHEST, HIGH);
        event.addPhaseOrdering(HIGH, Event.DEFAULT_PHASE);
        event.addPhaseOrdering(Event.DEFAULT_PHASE, LOW);
        event.addPhaseOrdering(LOW, LOWEST);
        for (EventPriority priority : PRIORITIES) {
            event.register(phase(priority), factory.apply(priority));
        }
    }

    static Identifier phase(EventPriority priority) {
        return switch (priority) {
            case HIGHEST -> HIGHEST;
            case HIGH -> HIGH;
            case NORMAL -> Event.DEFAULT_PHASE;
            case LOW -> LOW;
            case LOWEST -> LOWEST;
        };
    }

    /** Whether a platform event is worth building at this priority. Most callbacks have nobody listening. */
    public static boolean wanted(Class<? extends net.bananemdnsa.historystages.platform.bus.Event> type,
                                 EventPriority priority) {
        return EventBus.hasListeners(type, priority);
    }

    /** Whether anyone listens at all, for the mixin-fired events that post every priority at once. */
    public static boolean wanted(Class<? extends net.bananemdnsa.historystages.platform.bus.Event> type) {
        return EventBus.hasListeners(type);
    }

    /**
     * Posts {@code event} at {@code priority} and, if it ends up cancelled, also at every lower
     * priority before the caller ends the Fabric chain. Only receiveCanceled handlers run in that
     * tail, the same as they would have on NeoForge. Returns whether the event is cancelled.
     */
    public static <E extends net.bananemdnsa.historystages.platform.bus.Event & ICancellableEvent> boolean postCancellable(
            E event, EventPriority priority) {
        EventBus.post(event, priority);
        if (event.isCanceled()) {
            finishCancelled(event, priority);
        }
        return event.isCanceled();
    }

    /** Hands an already cancelled event to the priorities below {@code priority}. */
    public static void finishCancelled(net.bananemdnsa.historystages.platform.bus.Event event, EventPriority priority) {
        for (int i = priority.ordinal() + 1; i < PRIORITIES.length; i++) {
            if (EventBus.hasListeners(event.getClass(), PRIORITIES[i])) {
                EventBus.post(event, PRIORITIES[i]);
            }
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(HistoryStages.MOD_ID, path);
    }
}
