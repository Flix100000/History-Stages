package net.bananemdnsa.historystages.util.lock;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HotbarSelectionTest {

    private static IntPredicate locked(Integer... slots) {
        Set<Integer> set = Set.of(slots);
        return set::contains;
    }

    /** Scrolling down (positive direction) moves to higher slots, as vanilla does. */
    @Test
    void scrollSkipsLockedSlots() {
        assertEquals(4, HotbarSelection.next(1, 1, locked(2, 3)));
        assertEquals(1, HotbarSelection.next(4, -1, locked(2, 3)));
    }

    @Test
    void scrollWraps() {
        assertEquals(1, HotbarSelection.next(8, 1, locked(0)));
        assertEquals(7, HotbarSelection.next(0, -1, locked(8)));
    }

    @Test
    void allLockedKeepsCurrent() {
        assertEquals(5, HotbarSelection.next(5, 1, slot -> true));
        assertEquals(5, HotbarSelection.next(5, -1, slot -> true));
    }

    @Test
    void numberKeyOnLockedSlotKeepsCurrent() {
        assertEquals(4, HotbarSelection.accept(4, 2, locked(2)));
        assertEquals(6, HotbarSelection.accept(4, 6, locked(2)));
    }

    /** A selected slot that became locked moves to the nearest open one, so the hand is usable. */
    @Test
    void lockedCurrentSlotMovesToAnOpenOne() {
        assertEquals(1, HotbarSelection.settle(0, locked(0)));
        assertEquals(3, HotbarSelection.settle(3, locked(0)));
        assertEquals(0, HotbarSelection.settle(0, slot -> true));
    }
}
