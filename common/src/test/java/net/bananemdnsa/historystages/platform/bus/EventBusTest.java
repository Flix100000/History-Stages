package net.bananemdnsa.historystages.platform.bus;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bus carries every gameplay rule in the mod, and a handler that is never called looks exactly
 * like a rule that does not apply — so the ways a listener can go missing are what is checked
 * here, not the happy path alone.
 *
 * <p>The bus is static and shared by every test, so each test posts an event type of its own.
 */
class EventBusTest {

    static final List<String> CALLS = new ArrayList<>();

    static class Plain extends Event {}

    static class Ranked extends Event {}

    static class Stoppable extends Event implements ICancellableEvent {}

    static class Skippable extends Event implements ICancellableEvent {}

    static class Watched extends Event implements ICancellableEvent {}

    static class Flagged extends Event implements ICancellableEvent {}

    static class Other extends Event {}

    static class Unheard extends Event {}

    static class Parent extends Event {}

    static class Child extends Parent {}

    static class RankedParent extends Event {}

    static class RankedChild extends RankedParent {}

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
        public static void last(Ranked event) {
            CALLS.add("lowest");
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void first(Ranked event) {
            CALLS.add("highest");
        }

        @SubscribeEvent
        public static void middle(Ranked event) {
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

    public static class SkipWithoutReceiveCanceled {
        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void stop(Skippable event) {
            CALLS.add("skip-canceller");
            event.setCanceled(true);
        }

        @SubscribeEvent
        public static void normal(Skippable event) {
            CALLS.add("skip-normal");
        }

        @SubscribeEvent(priority = EventPriority.LOW)
        public static void low(Skippable event) {
            CALLS.add("skip-low");
        }
    }

    public static class ReceivesCanceled {
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void stop(Watched event) {
            CALLS.add("watch-canceller");
            event.setCanceled(true);
        }

        @SubscribeEvent
        public static void skipped(Watched event) {
            CALLS.add("watch-skipped");
        }

        // Runs after a cancel and may even take it back, as on NeoForge.
        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public static void observer(Watched event) {
            CALLS.add("watch-observer:" + event.isCanceled());
        }
    }

    public static class FlagSetter {
        @SubscribeEvent
        public static void stop(Flagged event) {
            event.setCanceled(true);
        }
    }

    public static class Thrower {
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void boom(Other event) {
            throw new IllegalStateException("handler is broken");
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void neverReached(Other event) {
            CALLS.add("after-throw");
        }
    }

    public static class ParentHandler {
        @SubscribeEvent
        public static void onParent(Parent event) {
            CALLS.add("parent:" + event.getClass().getSimpleName());
        }
    }

    public static class MixedRanks {
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void superLast(RankedParent event) {
            CALLS.add("super-lowest");
        }

        @SubscribeEvent
        public static void exactMiddle(RankedChild event) {
            CALLS.add("exact-normal");
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void superFirst(RankedParent event) {
            CALLS.add("super-highest");
        }
    }

    public static class WrongShape {
        @SubscribeEvent
        public static void twoArguments(Plain event, String extra) {
        }
    }

    @Test
    void postCallsHandlersInPriorityOrder() {
        CALLS.clear();
        EventBus.register(Ordered.class);
        EventBus.post(new Ranked());
        assertEquals(List.of("highest", "normal", "lowest"), CALLS);
    }

    @Test
    void canceledEventSkipsHandlersWithoutReceiveCanceled() {
        CALLS.clear();
        EventBus.register(SkipWithoutReceiveCanceled.class);
        EventBus.post(new Skippable());
        assertEquals(List.of("skip-canceller"), CALLS,
                "a listener after a cancel would be deciding about something that is not happening");
    }

    @Test
    void receiveCanceledHandlerStillRuns() {
        CALLS.clear();
        EventBus.register(ReceivesCanceled.class);
        EventBus.post(new Watched());
        assertEquals(List.of("watch-canceller", "watch-observer:true"), CALLS);
    }

    @Test
    void postReturnsSameInstanceWithCancelFlag() {
        EventBus.register(FlagSetter.class);
        Flagged event = new Flagged();
        assertFalse(event.isCanceled());
        Flagged returned = EventBus.post(event);
        assertSame(event, returned);
        assertTrue(returned.isCanceled());
    }

    @Test
    void aStaticHandlerIsCalledAndAnInstanceOneIsNot() {
        CALLS.clear();
        EventBus.register(StaticHandler.class);
        EventBus.post(new Plain());
        assertTrue(CALLS.contains("static"));
        assertFalse(CALLS.contains("instance-via-class"),
                "registering a class must take its static methods and leave the instance ones alone");
    }

    @Test
    void anInstanceHandlerIsCalled() {
        CALLS.clear();
        InstanceHandler handler = new InstanceHandler();
        EventBus.register(handler);
        EventBus.post(new Plain());
        EventBus.unregister(handler);
        assertTrue(CALLS.contains("instance"));
    }

    @Test
    void cancellingStopsTheRest() {
        CALLS.clear();
        EventBus.register(Canceller.class);
        Stoppable event = EventBus.post(new Stoppable());
        assertTrue(event.isCanceled());
        assertEquals(List.of("canceller"), CALLS);
    }

    @Test
    void handlerExceptionPropagatesFromPost() {
        CALLS.clear();
        EventBus.register(Thrower.class);
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> EventBus.post(new Other()),
                "a swallowed exception before setCanceled would silently open a lock");
        assertEquals("handler is broken", thrown.getMessage(), "the handler's own exception, not a reflection wrapper");
        assertEquals(List.of(), CALLS);
    }

    @Test
    void handlerForSupertypeReceivesSubclassEvent() {
        CALLS.clear();
        EventBus.register(ParentHandler.class);
        EventBus.post(new Child());
        EventBus.post(new Parent());
        assertEquals(List.of("parent:Child", "parent:Parent"), CALLS);
    }

    @Test
    void priorityOrderHoldsAcrossSupertypeAndExactListeners() {
        CALLS.clear();
        EventBus.register(MixedRanks.class);
        EventBus.post(new RankedChild());
        assertEquals(List.of("super-highest", "exact-normal", "super-lowest"), CALLS);
        assertTrue(EventBus.hasListeners(RankedChild.class));
    }

    @Test
    void aHandlerWithTheWrongShapeIsRefusedLoudly() {
        assertThrows(IllegalArgumentException.class, () -> EventBus.register(WrongShape.class),
                "a silently ignored handler is indistinguishable from a rule that does not apply");
    }

    @Test
    void anEventNobodyListensForIsHandedBackUnchanged() {
        Unheard unheard = new Unheard();
        assertSame(unheard, EventBus.post(unheard));
        assertFalse(EventBus.hasListeners(Unheard.class));
    }
}
