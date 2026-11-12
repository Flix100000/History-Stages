package net.bananemdnsa.historystages.platform.bus;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The bus the handlers hang on.
 *
 * <p>NeoForge finds handler classes by scanning the jar for an annotation. Nothing does that here,
 * so the classes are handed over explicitly by the initializer — and a test walks the source tree
 * to make sure the list has not fallen behind, because a handler that is merely never registered
 * fails silently and looks exactly like a rule that does not apply.
 *
 * <p>Dispatch follows NeoForge: a listener for an event class also receives every subclass of it,
 * so a handler taking {@code LivingEvent} hears {@code LivingDeathEvent} too. Handlers moved over
 * from the main branch rely on that and would otherwise never fire.
 */
public final class EventBus {

    private record Listener(Method method, Object instance, EventPriority priority, boolean receiveCanceled,
                            long order) {}

    // Highest priority first; within a priority, in the order the listeners were registered,
    // regardless of whether they listen for the exact class or a supertype.
    private static final Comparator<Listener> DISPATCH_ORDER =
            Comparator.<Listener>comparingInt(listener -> listener.priority().ordinal())
                    .thenComparingLong(Listener::order);

    private static final AtomicLong NEXT_ORDER = new AtomicLong();

    /** Listeners by the event class they were registered for. Guarded by the class lock. */
    private static final Map<Class<?>, List<Listener>> LISTENERS = new ConcurrentHashMap<>();

    /**
     * Listeners for each posted event class, supertypes merged in. Built on first post and dropped
     * on every change, so posting stays a single map lookup during play.
     */
    private static final Map<Class<?>, List<Listener>> RESOLVED = new ConcurrentHashMap<>();

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
            addEntry(method.getParameterTypes()[0],
                    new Listener(method, instance, annotation.priority(), annotation.receiveCanceled(),
                            NEXT_ORDER.getAndIncrement()));
        }
    }

    private static synchronized void addEntry(Class<?> eventType, Listener listener) {
        LISTENERS.computeIfAbsent(eventType, key -> new ArrayList<>()).add(listener);
        RESOLVED.clear();
    }

    /**
     * Hands the event to every listener for its class or any superclass, and gives it back.
     *
     * <p>Once an event has been cancelled the rest of the listeners are skipped: they would be
     * deciding about something that is no longer going to happen. Only a listener that asked for
     * {@code receiveCanceled} still sees it, the same rule NeoForge applies.
     *
     * <p>A handler that throws is not caught. Swallowing it would let a lock handler that failed
     * before {@code setCanceled} wave the action through as if nothing were locked; NeoForge
     * propagates the exception as well.
     */
    public static <T extends Event> T post(T event) {
        List<Listener> listeners = resolve(event.getClass());
        if (listeners.isEmpty()) {
            return event;
        }
        boolean cancellable = event instanceof ICancellableEvent;
        for (Listener listener : listeners) {
            if (cancellable && !listener.receiveCanceled() && ((ICancellableEvent) event).isCanceled()) {
                continue;
            }
            if (listener.method() == null) {
                @SuppressWarnings("unchecked")
                Consumer<Event> consumer = (Consumer<Event>) listener.instance();
                consumer.accept(event);
            } else {
                invoke(listener, event);
            }
        }
        return event;
    }

    private static void invoke(Listener listener, Event event) {
        try {
            listener.method().invoke(listener.instance(), event);
        } catch (InvocationTargetException e) {
            // Rethrow what the handler threw, not the reflection wrapper around it.
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(cause);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<Listener> resolve(Class<?> eventType) {
        List<Listener> cached = RESOLVED.get(eventType);
        if (cached != null) {
            return cached;
        }
        synchronized (EventBus.class) {
            List<Listener> merged = new ArrayList<>();
            for (Class<?> type = eventType; type != null && Event.class.isAssignableFrom(type);
                 type = type.getSuperclass()) {
                List<Listener> own = LISTENERS.get(type);
                if (own != null) {
                    merged.addAll(own);
                }
            }
            merged.sort(DISPATCH_ORDER);
            List<Listener> result = List.copyOf(merged);
            RESOLVED.put(eventType, result);
            return result;
        }
    }

    /**
     * Adds a single listener without a class to scan.
     *
     * <p>For the places that hand over a lambda rather than a marked method — an integration that
     * only wants to hear about one thing and has no handler class of its own.
     */
    public static <T extends Event> void addListener(Class<T> eventType, Consumer<T> listener) {
        addEntry(eventType, new Listener(null, listener, EventPriority.NORMAL, false, NEXT_ORDER.getAndIncrement()));
    }

    /**
     * Takes a listener off again: a lambda handed to {@link #addListener}, or an object handed to
     * {@link #register(Object)}. For the code that only listens for a while, a test above all.
     */
    public static synchronized void unregister(Object listener) {
        for (List<Listener> list : LISTENERS.values()) {
            list.removeIf(entry -> entry.instance() == listener);
        }
        RESOLVED.clear();
    }

    /** Whether anything listens for this event or a superclass of it, so a caller can skip building one. */
    public static boolean hasListeners(Class<? extends Event> eventType) {
        return !resolve(eventType).isEmpty();
    }
}
