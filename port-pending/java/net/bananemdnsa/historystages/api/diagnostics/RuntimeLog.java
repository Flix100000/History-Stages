package net.bananemdnsa.historystages.api.diagnostics;

import net.bananemdnsa.historystages.util.DebugLogger;
import net.minecraft.server.level.ServerPlayer;

/**
 * Writes lines into History Stages' runtime log ({@code config/historystages/logs/runtime-*.log}).
 *
 * <p>Does nothing unless the pack has {@code enableRuntimeLogging} on, so calling it on a hot path
 * costs one config read. Lines are buffered and written every 30 seconds.
 */
public final class RuntimeLog {

    private RuntimeLog() {}

    public static void write(RuntimeCategory category, String message) {
        DebugLogger.runtime(category, message);
    }

    /** The line starts with {@code <player name>}. */
    public static void write(RuntimeCategory category, ServerPlayer player, String message) {
        DebugLogger.runtime(category, player.getName().getString(), message);
    }

    /**
     * At most one line per {@code key} every five seconds, for things that can happen every tick.
     * Keys are kept apart per category, so an addon cannot silence one of History Stages' lines.
     */
    public static void throttled(RuntimeCategory category, String key, String message) {
        DebugLogger.runtimeThrottled(category, category.id() + "|" + key, message);
    }
}
