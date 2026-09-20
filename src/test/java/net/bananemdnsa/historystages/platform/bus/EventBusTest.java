package net.bananemdnsa.historystages.platform.bus;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bus carries every gameplay rule in the mod, and a handler that is never called looks exactly
 * like a rule that does not apply — so the ways a listener can go missing are what is checked
 * here, not the happy path alone.
 */
class EventBusTest {

    static final List<String> CALLS = new ArrayList<>();

    static class Plain extends Event {}

    static class Stoppable extends Event implements ICancellableEvent {}

    static class Other extends Event {}

    public static class StaticHandler {
        @SubscribeEvent
        public static void onPlain(Plain event) {
            CALLS.add("static");
        }

        // Registering the class must not pick this up.
        @SubscribeEvent
        public void onPlainInstance(Plain event) {
            CALLS.add("instance-via-class");
        }
    }

    public static class InstanceHandler {
        @SubscribeEvent
        public void onPlain(Plain event) {
            CALLS.add("instance");
        }
    }

    public static class Ordered {
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void last(Plain event) {
            CALLS.add("lowest");
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void first(Plain event) {
            CALLS.add("highest");
        }

        @SubscribeEvent
        public static void middle(Plain event) {
            CALLS.add("normal");
        }
    }

    public static class Canceller {
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void stop(Stoppable event) {
            CALLS.add("canceller");
            event.setCanceled(true);
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void afterwards(Stoppable event) {
            CALLS.add("after-cancel");
        }
    }

    public static class Thrower {
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void boom(Other event) {
            throw new IllegalStateException("handler is broken");
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void stillRuns(Other event) {
            CALLS.add("survivor");
        }
    }

    public static class WrongShape {
        @SubscribeEvent
        public static void twoArguments(Plain event, String extra) {
        }
    }

    @Test
    void aStaticHandlerIsCalledAndAnInstanceOneIsNot() {
        CALLS.clear();
        EventBus.register(StaticHandler.class);
        EventBus.post(new Plain());
        assertEquals(List.of("static"), CALLS,
                "registering a class must take its static methods and leave the instance ones alone");
    }

    @Test
    void anInstanceHandlerIsCalled() {
        CALLS.clear();
        EventBus.register(new InstanceHandler());
        EventBus.post(new Plain());
        assertTrue(CALLS.contains("instance"));
    }

    @Test
    void handlersRunHighestFirst() {
        CALLS.clear();
        EventBus.register(Ordered.class);
        EventBus.post(new Plain());
        List<String> ordered = CALLS.stream().filter(c -> List.of("highest", "normal", "lowest").contains(c)).toList();
        assertEquals(List.of("highest", "normal", "lowest"), ordered);
    }

    @Test
    void cancellingStopsTheRest() {
        CALLS.clear();
        EventBus.register(Canceller.class);
        Stoppable event = EventBus.post(new Stoppable());
        assertTrue(event.isCanceled());
        assertEquals(List.of("canceller"), CALLS,
                "a listener after a cancel would be deciding about something that is not happening");
    }

    @Test
    void aBrokenHandlerDoesNotTakeTheOthersWithIt() {
        CALLS.clear();
        EventBus.register(Thrower.class);
        EventBus.post(new Other());
        assertEquals(List.of("survivor"), CALLS,
                "a broken rule should lose its own lock, not the interaction it hung on");
    }

    @Test
    void aHandlerWithTheWrongShapeIsRefusedLoudly() {
        assertThrows(IllegalArgumentException.class, () -> EventBus.register(WrongShape.class),
                "a silently ignored handler is indistinguishable from a rule that does not apply");
    }

    @Test
    void anEventNobodyListensForIsHandedBackUnchanged() {
        CALLS.clear();
        Plain event = new Plain();
        assertEquals(event, EventBus.post(event));
    }
}
