package net.bananemdnsa.historystages.reload;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import com.mojang.logging.LogUtils;

import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

/**
 * PROTOTYPE. {@code /hsreloadlab bench} — the measuring run from the test checklist, done by the
 * server itself so every pack is measured the same way.
 *
 * <p>For each mode it opens all bench stages, then locks and unlocks each of them on its own, from
 * the smallest to the largest, waiting after every step until the reload is through and its
 * {@link ReloadStats} line is written. The steps go through the real {@code /history}
 * command, so they take the same path an admin or a quest would.
 *
 * <p>Stages that are not in the pack are left out, so the same command works in the dev world and
 * in a pack that only has some of them.
 */
public final class ReloadBench {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Smallest to largest; the first one gates no recipe and must not reload at all. */
    public static final List<String> STAGES = List.of(
            "rt_08_stage_without_recipes",
            "rt_size1_one_recipe",
            "rt_size2_ten_items",
            "rt_size3_dye_families",
            "rt_size4_one_big_mod",
            "rt_size5_worst_case");

    /** Ticks to let the server settle after a reload before the next step. */
    private static final int SETTLE_TICKS = 40;

    private record Step(String label, StageReloadMode mode, String command, boolean recorded) {}

    private static final Deque<Step> queue = new ArrayDeque<>();
    private static final List<String> results = new ArrayList<>();
    private static CommandSourceStack source;
    private static boolean waiting;
    private static int settle;
    private static Step current;
    private static StageReloadMode modeBefore;

    private ReloadBench() {}

    public static synchronized boolean isRunning() {
        return source != null;
    }

    /** Queues the whole run. Returns how many bench stages the pack has. */
    public static synchronized int start(CommandSourceStack from, List<StageReloadMode> modes) {
        List<String> present = new ArrayList<>();
        for (String id : STAGES) {
            if (StageManager.getStages().containsKey(id)) present.add(id);
        }
        if (present.isEmpty()) return 0;

        queue.clear();
        results.clear();
        modeBefore = LightReload.modeOverride();
        for (StageReloadMode mode : modes) {
            String name = mode.name().toLowerCase(Locale.ROOT);
            // All of them open first, so each step below moves only what its own stage gates.
            // Locked together, the largest would hide everything the smaller ones gate and
            // their steps would measure nothing.
            for (String id : present) {
                queue.add(new Step(name + " setup unlock " + id, mode, "history global unlock " + id, false));
            }
            for (String id : present) {
                queue.add(new Step(name + " lock " + id, mode, "history global lock " + id, true));
                queue.add(new Step(name + " unlock " + id, mode, "history global unlock " + id, true));
            }
        }
        source = from;
        waiting = false;
        current = null;
        return present.size();
    }

    public static synchronized void stop() {
        queue.clear();
        finish("stopped");
    }

    /** Called once per server tick, before the requested reload runs. */
    public static synchronized void onTick(MinecraftServer server) {
        if (source == null) return;

        if (waiting) {
            if (BackgroundReload.isRunning(server) || !ReloadStats.isIdle()) {
                settle = 0;
                return;
            }
            if (++settle < SETTLE_TICKS) return;
            waiting = false;
            if (current.recorded()) {
                String line = ReloadStats.lastLine();
                String row = current.label() + " | " + (line == null ? "no reload line" : line);
                results.add(row);
                LOGGER.info("[HistoryStages/ReloadBench] {}", row);
                source.sendSystemMessage(Component.translatable("command.historystages.reloadlab.bench.row", row));
            }
        }

        Step next = queue.poll();
        if (next == null) {
            finish("done");
            return;
        }
        current = next;
        LightReload.setMode(next.mode());
        ReloadStats.clearLastLine();
        server.getCommands().performPrefixedCommand(source.withSuppressedOutput(), next.command());
        waiting = true;
        settle = 0;
    }

    private static void finish(String how) {
        if (source == null) return;
        LightReload.setMode(modeBefore); // null hands it back to the config
        LOGGER.info("[HistoryStages/ReloadBench] ===== bench {} ({} rows) =====", how, results.size());
        results.forEach(row -> LOGGER.info("[HistoryStages/ReloadBench] {}", row));
        source.sendSystemMessage(Component.translatable("command.historystages.reloadlab.bench." + how,
                results.size()));
        source = null;
        current = null;
        waiting = false;
    }
}
