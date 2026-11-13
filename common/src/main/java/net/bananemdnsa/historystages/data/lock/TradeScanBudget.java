package net.bananemdnsa.historystages.data.lock;

import java.util.function.LongSupplier;

/**
 * A clock the merchant scan keeps an eye on, so one slow trade cannot hold the server.
 *
 * <p>A trade recipe is asked what it would hand over, and nearly all of them answer in
 * microseconds. One does not: a cartographer's treasure map goes and searches the world for the
 * structure the map would point at, generating terrain until it finds one. That is the eighteen
 * seconds the server once stood still for, on the thread that also runs commands.
 *
 * <p>The vanilla one is left out by name, which is free and stops it before it starts. This is for
 * the ones that cannot be named: a mod's own map trade, or anything else that decides to go
 * looking. It cannot make the first such run cheap — a search that has started cannot be called
 * back — but it stops the scan from doing it again seven more times for that listing, and then for
 * every other listing behind it.
 *
 * <p>Separate from the scan so the arithmetic can be tested against a clock that is told what time
 * it is.
 */
public final class TradeScanBudget {

    /**
     * Long enough that no honest trade recipe will ever reach it, short enough that hitting it
     * twice is still not something a player would call a freeze.
     */
    public static final long SLOW_LISTING_MILLIS = 50;

    /** The whole scan. A healthy one on a large pack lands well under a tenth of this. */
    public static final long TOTAL_MILLIS = 2_000;

    private final long slowListingMillis;
    private final long totalMillis;
    private final LongSupplier clock;
    private final long startedAt;

    public TradeScanBudget(LongSupplier clock) {
        this(SLOW_LISTING_MILLIS, TOTAL_MILLIS, clock);
    }

    public TradeScanBudget(long slowListingMillis, long totalMillis, LongSupplier clock) {
        this.slowListingMillis = slowListingMillis;
        this.totalMillis = totalMillis;
        this.clock = clock;
        this.startedAt = clock.getAsLong();
    }

    public long now() {
        return clock.getAsLong();
    }

    public long elapsed() {
        return clock.getAsLong() - startedAt;
    }

    /** The scan as a whole has had its time; whatever has been found by now is the answer. */
    public boolean spent() {
        return elapsed() >= totalMillis;
    }

    /** One run, begun at {@code runStartedAt}, took long enough to give up on its listing. */
    public boolean tooSlow(long runStartedAt) {
        return clock.getAsLong() - runStartedAt >= slowListingMillis;
    }
}
