package net.bananemdnsa.historystages.platform.bus;

import net.bananemdnsa.historystages.util.DebugLogger;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The bus the handlers hang on.
 *
 * <p>NeoForge finds handler classes by scanning the jar for an annotation. Nothing does that here,
 * so the classes are handed over explicitly by the initializer — and a test walks the source tree
 * to make sure the list has not fallen behind, because a handler that is merely never registered
 * fails silently and looks exactly like a rule that does not apply.
 *
 * <p>Dispatch is by the event's own class, not its supertypes. Every handler in this mod
 * subscribes to a concrete event, and matching supertypes as well would mean a handler for
 * {@code PlayerInteractEvent} quietly receiving right-clicks meant for the block variant.
 */
public final class EventBus {

    private record Listener(Method method, Object instance, EventPriority priority) {}

    private static final Map<Class<?>, List<Listener>> LISTENERS = new ConcurrentHashMap<>();

    private EventBus() {}

    /** Registers the static handler methods of a class. */
    public static void register(Class<?> handlerClass) {
        add(handlerClass, null);
    }

    /** Registers the instance handler methods of an object. */
    public static void register(Object handler) {
        add(handler.getClass(), handler);
    }

    private static void add(Class<?> owner, Object instance) {
        for (Method method : owner.getDeclaredMethods()) {
            SubscribeEvent annotation = method.getAnnotation(SubscribeEvent.class);
            if (annotation == null) {
                continue;
            }
            boolean isStatic = Modifier.isStatic(method.getModifiers());
            if (isStatic == (instance != null)) {
                // Registering a class picks up its static methods, registering an object its
                // instance ones. Silently taking the other kind would call a handler with the
                // wrong receiver, or not at all.
                continue;
            }
            if (method.getParameterCount() != 1 || !Event.class.isAssignableFrom(method.getParameterTypes()[0])) {
                throw new IllegalArgumentException(
                        owner.getName() + "." + method.getName() + " is marked @SubscribeEvent but does not take a single event");
            }
            method.setAccessible(true);
            Class<?> eventType = method.getParameterTypes()[0];
            List<Listener> list = LISTENERS.computeIfAbsent(eventType, key -> new ArrayList<>());
            synchronized (list) {
                list.add(new Listener(method, instance, annotation.priority()));
                list.sort(Comparator.comparingInt(listener -> listener.priority().ordinal()));
            }
        }
    }

    /**
     * Hands the event to every listener for its class and gives it back.
     *
     * <p>Once an event has been cancelled the rest of the listeners are skipped: they would be
     * deciding about something that is no longer going to happen.
     *
     * <p>A handler that throws must not take the game with it. The rules this bus carries are
     * gates on ordinary play — a broken one should lose its own lock, not the interaction, the
     * tick or the spawn it was attached to.
     */
    public static <T extends Event> T post(T event) {
        List<Listener> list = LISTENERS.get(event.getClass());
        if (list == null) {
            return event;
        }
        List<Listener> snapshot;
        synchronized (list) {
            snapshot = List.copyOf(list);
        }
        boolean cancellable = event instanceof ICancellableEvent;
        for (Listener listener : snapshot) {
            if (cancellable && ((ICancellableEvent) event).isCanceled()) {
                break;
            }
            try {
                listener.method().invoke(listener.instance(), event);
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                DebugLogger.warn("Events", listener.method().getDeclaringClass().getSimpleName()
                        + "." + listener.method().getName() + " failed on "
                        + event.getClass().getSimpleName() + ": " + cause);
            }
        }
        return event;
    }

    /** Whether anything at all listens for this event, so a caller can skip building one. */
    public static boolean hasListeners(Class<? extends Event> eventType) {
        List<Listener> list = LISTENERS.get(eventType);
        return list != null && !list.isEmpty();
    }
}
