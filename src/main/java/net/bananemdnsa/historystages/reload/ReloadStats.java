package net.bananemdnsa.historystages.reload;

import java.util.Locale;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

/**
 * PROTOTYPE. One log line per reload a stage change sets off, with the numbers that make two test
 * runs comparable: how many recipes the change moved, how long the reload took in all, and — the
 * one a player feels — the longest stretch the server went without a tick while it ran.
 *
 * <p>The tick gap is measured from tick to tick rather than inside the reload, so the blocking
 * full reload, the background one and the targeted one are all measured by the same ruler. The
 * line is written on the first tick after the reload, since that tick closes the last gap.
 *
 * <p>Grep the log for {@code [HistoryStages/ReloadStats]}.
 */
public final class ReloadStats {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean active;
    private static boolean ended;
    private static String mode;
    private static int gatedBefore;
    private static int gatedAfter;
    private static int recipes;
    private static long startNanos;
    private static long endNanos;
    private static long lastTickNanos;
    private static long worstGapNanos;
    private static int ticks;
    private static String error;

    private ReloadStats() {}

    /** A reload is starting. A second one while one is measured is folded into it. */
    public static synchronized void begin(String modeName, int before, int after, int totalRecipes) {
        if (active && !ended) {
            mode = mode + "+" + modeName;
            gatedAfter = after;
            return;
        }
        active = true;
        ended = false;
        mode = modeName;
        gatedBefore = before;
        gatedAfter = after;
        recipes = totalRecipes;
        startNanos = System.nanoTime();
        lastTickNanos = startNanos;
        worstGapNanos = 0;
        ticks = 0;
        error = null;
    }

    /** The reload is through. Written out on the next tick, which closes the last gap. */
    public static synchronized void end(Throwable failure) {
        if (!active) return;
        ended = true;
        endNanos = System.nanoTime();
        if (failure != null) error = String.valueOf(failure.getMessage());
    }

    /** Called once per server tick. */
    public static synchronized void onTick() {
        if (!active) return;
        long now = System.nanoTime();
        worstGapNanos = Math.max(worstGapNanos, now - lastTickNanos);
        lastTickNanos = now;
        if (!ended) {
            ticks++;
            return;
        }
        LOGGER.info(String.format(Locale.ROOT,
                "[HistoryStages/ReloadStats] mode=%s recipes=%d gated=%d->%d total=%dms ticksDuring=%d "
                        + "longestTickGap=%dms%s",
                mode, recipes, gatedBefore, gatedAfter, (endNanos - startNanos) / 1_000_000, ticks,
                worstGapNanos / 1_000_000, error == null ? "" : " FAILED: " + error));
        active = false;
    }

    /** Server going away. */
    public static synchronized void forget() {
        active = false;
        ended = false;
    }
}
