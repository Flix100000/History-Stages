package net.bananemdnsa.historystages.data.relock;

import com.mojang.logging.LogUtils;
import net.bananemdnsa.historystages.api.trigger.StateView;
import net.bananemdnsa.historystages.events.StateCapture;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.bananemdnsa.historystages.util.DebugLogger;
import org.slf4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** The once-a-second re-lock work, hung off AutoTriggerEventBridge's tick. */
public final class RelockPolls {

    private RelockPolls() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Stages whose check already threw once, so the console gets one stack trace, not one a second. */
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    /**
     * Runs one stage's check of one poll. An addon's {@code holds()} may throw, and an exception
     * escaping the server tick takes the whole server down; a broken stage should only stop itself.
     */
    static void guarded(String stageId, boolean individual, Runnable check) {
        try {
            check.run();
        } catch (RuntimeException ex) {
            String key = (individual ? "individual:" : "global:") + stageId;
            if (REPORTED.add(key)) {
                LOGGER.warn("[HistoryStages] Re-lock check of stage '{}' threw. It is skipped while it keeps failing.",
                        stageId, ex);
            }
            DebugLogger.runtimeThrottled("Re-lock", "relock-error:" + key,
                    "Check of '" + stageId + "' threw " + ex + ". Skipped this poll.");
        }
    }

    /** After a reload a fixed stage that breaks again deserves a fresh stack trace. */
    static void resetReported() { REPORTED.clear(); }

    public static boolean anyDue() {
        return ConditionalStageManager.hasAny() || LockTriggerManager.hasNegations();
    }

    public static void pollPlayer(ServerPlayer player, StateView view) {
        if (!anyDue() || view == null) return;
        ConditionalStageManager.pollPlayer(player, view);
        LockTriggerManager.pollPlayer(player, view);
    }

    public static void pollWorld(MinecraftServer server) {
        if (!anyDue()) return;
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;
        StateView view = StateCapture.forWorld(overworld);
        ConditionalStageManager.pollWorld(overworld, view);
        LockTriggerManager.pollWorld(overworld, view);
    }
}
