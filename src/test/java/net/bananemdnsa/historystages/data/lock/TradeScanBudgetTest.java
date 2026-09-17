package net.bananemdnsa.historystages.data.lock;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeScanBudgetTest {

    /** A clock that only moves when told to, so the arithmetic is not racing a real one. */
    private static final class FakeClock {
        private final AtomicLong now = new AtomicLong(1_000);

        long read() {
            return now.get();
        }

        void advance(long millis) {
            now.addAndGet(millis);
        }
    }

    @Test
    void anOfferThatAnswersAtOnceIsAskedAgain() {
        FakeClock clock = new FakeClock();
        TradeScanBudget budget = new TradeScanBudget(50, 2_000, clock::read);

        long runStartedAt = budget.now();
        clock.advance(1);

        assertFalse(budget.tooSlow(runStartedAt));
        assertFalse(budget.spent());
    }

    @Test
    void anOfferThatWentLookingIsNotAskedAgain() {
        FakeClock clock = new FakeClock();
        TradeScanBudget budget = new TradeScanBudget(50, 2_000, clock::read);

        long runStartedAt = budget.now();
        clock.advance(50);

        assertTrue(budget.tooSlow(runStartedAt));
    }

    @Test
    void theScanStopsOnceItHasHadItsTime() {
        FakeClock clock = new FakeClock();
        TradeScanBudget budget = new TradeScanBudget(50, 2_000, clock::read);

        clock.advance(1_999);
        assertFalse(budget.spent());

        clock.advance(1);
        assertTrue(budget.spent());
    }

    /**
     * The case the whole budget exists for: many listings, each just under the per-listing
     * threshold, adding up. One slow trade is survivable; a few dozen of them is the freeze.
     */
    @Test
    void manyMerelySlowOffersStillEndTheScan() {
        FakeClock clock = new FakeClock();
        TradeScanBudget budget = new TradeScanBudget(50, 2_000, clock::read);

        for (int listing = 0; listing < 41; listing++) {
            long runStartedAt = budget.now();
            clock.advance(49);
            assertFalse(budget.tooSlow(runStartedAt),
                    "49ms is under the per-listing threshold and must not trip it on its own");
        }

        assertTrue(budget.spent(),
                "forty-one listings at 49ms each is over two seconds of standing still — the total "
                        + "budget is what has to catch that, because no single one was slow");
    }

    /** The default thresholds are the ones the scanner uses; a typo there would be silent. */
    @Test
    void theDefaultsAreTheOnesTheScannerRunsWith() {
        FakeClock clock = new FakeClock();
        TradeScanBudget budget = new TradeScanBudget(clock::read);

        long runStartedAt = budget.now();
        clock.advance(TradeScanBudget.SLOW_LISTING_MILLIS);
        assertTrue(budget.tooSlow(runStartedAt));

        clock.advance(TradeScanBudget.TOTAL_MILLIS);
        assertTrue(budget.spent());
    }
}
