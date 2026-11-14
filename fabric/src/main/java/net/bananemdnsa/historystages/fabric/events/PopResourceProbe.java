package net.bananemdnsa.historystages.fabric.events;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts calls to {@code Block.popResource} while a GameTest watches. That method is where other
 * Fabric mods hook drops, so a test can prove a plain break still goes through it. Off otherwise.
 */
public final class PopResourceProbe {

    private static volatile boolean watching;
    private static final AtomicInteger CALLS = new AtomicInteger();

    private PopResourceProbe() {}

    public static void start() {
        CALLS.set(0);
        watching = true;
    }

    public static int stop() {
        watching = false;
        return CALLS.get();
    }

    public static void hit() {
        if (watching) CALLS.incrementAndGet();
    }
}
