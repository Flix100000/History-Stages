package net.bananemdnsa.historystages.client.display;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.client.cache.ClientStageStates;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.logic.Condition;
import net.bananemdnsa.historystages.data.logic.LogicBlock;
import net.bananemdnsa.historystages.data.logic.LogicGroups;
import net.bananemdnsa.historystages.data.logic.StageLogic;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * "⛔ Blocked while: …" as lines, for the pedestal, the graph panel and the scroll tooltip.
 *
 * <p>The client already holds every stage file and its own unlocked sets, so it answers "blocked?"
 * itself instead of waiting for a packet. For an individual stage that means the local player's
 * view, which is the only one a screen on this client ever shows.
 */
public final class BlockedLines {

    private BlockedLines() {}

    /** Is this stage blocked for the local player right now? */
    public static StageLogic.Blocked forLocalPlayer(String stageId, boolean individual) {
        StageEntry entry = individual ? StageManager.getIndividualStages().get(stageId)
                : StageManager.getStages().get(stageId);
        if (entry == null || !entry.hasLogic()) return StageLogic.Blocked.NONE;
        return StageLogic.blocked(entry.getLogicBlocks(),
                individual ? StageScope.INDIVIDUAL : StageScope.GLOBAL,
                ClientStageStates.global(), ClientStageStates.individual());
    }

    /** Whether the local player may see this stage. */
    public static StageLogic.Visibility visibilityForLocalPlayer(String stageId, boolean individual) {
        StageEntry entry = individual ? StageManager.getIndividualStages().get(stageId)
                : StageManager.getStages().get(stageId);
        if (entry == null || !entry.hasLogic()) return StageLogic.Visibility.VISIBLE;
        return StageLogic.visibility(entry.getLogicBlocks(),
                individual ? StageScope.INDIVIDUAL : StageScope.GLOBAL,
                ClientStageStates.global(), ClientStageStates.individual());
    }

    /**
     * The name to show the local player: the display name, or "???" while the stage is hidden
     * from them. For places where the stage explains something and has to stay mentioned.
     */
    public static String displayName(String stageId, boolean individual) {
        StageEntry entry = individual ? StageManager.getIndividualStages().get(stageId)
                : StageManager.getStages().get(stageId);
        String name = entry != null ? entry.getDisplayName() : stageId;
        // Something the player already has is never hidden from them.
        boolean unlocked = (individual ? ClientStageStates.individual() : ClientStageStates.global()).isUnlocked(stageId);
        if (unlocked) return name;
        return visibilityForLocalPlayer(stageId, individual) == StageLogic.Visibility.VISIBLE
                ? name : net.bananemdnsa.historystages.data.logic.StageLogicGate.HIDDEN_NAME;
    }

    /** What researching the stage costs the local player right now. */
    public static StageLogic.CostFactors costForLocalPlayer(String stageId, boolean individual) {
        StageEntry entry = individual ? StageManager.getIndividualStages().get(stageId)
                : StageManager.getStages().get(stageId);
        if (entry == null || !entry.hasLogic()) return StageLogic.CostFactors.NONE;
        return StageLogic.cost(entry.getLogicBlocks(),
                individual ? StageScope.INDIVIDUAL : StageScope.GLOBAL,
                ClientStageStates.global(), ClientStageStates.individual());
    }

    /**
     * The heading line. Its wording is edited in one place, the "blocked" row of the scroll
     * tooltip layout, and shows the same on the pedestal and in the graph.
     */
    public static MutableComponent header() {
        String custom = templateText("blocked");
        if (!custom.isEmpty()) return Component.literal(custom.replace('&', '§'));
        return Component.translatable(showReason()
                ? "gui.historystages.blocked.header" : "gui.historystages.blocked.plain");
    }

    /** A layout row's custom text, or empty when it uses the built-in wording. */
    static String templateText(String id) {
        var line = net.bananemdnsa.historystages.data.tooltip.ScrollTooltipLayout.line(id);
        return line == null || line.text() == null ? "" : line.text();
    }

    public static boolean showReason() {
        return Config.VISUAL.blockedDisplay.get() == Config.Visual.BlockedDisplay.REASON;
    }

    /**
     * One line per condition term of the blocks that hold, unstyled; an any-of group of more than
     * one term gets a "One of:" line and indented terms. Empty under PLAIN.
     */
    public static List<Line> reasonLines(StageLogic.Blocked blocked, boolean ownerIndividual) {
        if (!blocked.isBlocked() || !showReason()) return List.of();
        List<Line> out = new ArrayList<>();
        for (LogicBlock block : blocked.matching()) out.addAll(conditionLines(block, ownerIndividual));
        return out;
    }

    /**
     * The condition of one block as lines, whatever its type. Empty under PLAIN, which decides for
     * every block type how much a player is told about the rules behind a stage.
     */
    public static List<Line> conditionLines(LogicBlock block, boolean ownerIndividual) {
        if (!showReason()) return List.of();
        StageScope owner = ownerIndividual ? StageScope.INDIVIDUAL : StageScope.GLOBAL;
        List<Line> out = new ArrayList<>();
        var groups = LogicGroups.fromCondition(block.condition());
        if (groups.isEmpty()) {
            // A hand-written tree the editor cannot draw: list what it asks about.
            Condition.forEachTerm(block.condition(), term ->
                    out.add(Line.term(stageName(term.stageId(), term.scope(), owner), true, 0)));
            return out;
        }
        for (LogicGroups.Group group : groups.get().groups()) {
            if (!group.all() && group.terms().size() > 1) {
                out.add(Line.anyOf());
                for (LogicGroups.Term term : group.terms()) {
                    out.add(Line.term(stageName(term.stageId(), term.scope(), owner), term.unlocked(), 1));
                }
            } else {
                for (LogicGroups.Term term : group.terms()) {
                    out.add(Line.term(stageName(term.stageId(), term.scope(), owner), term.unlocked(), 0));
                }
            }
        }
        return out;
    }

    // ---- cost ------------------------------------------------------------------------

    /** "Time -50%, XP -25%" for one cost block's own percentages. */
    public static MutableComponent costSummary(LogicBlock block) {
        return costSummary(
                net.bananemdnsa.historystages.data.logic.LogicBlockParams.percent(block, "time") / 100.0,
                net.bananemdnsa.historystages.data.logic.LogicBlockParams.percent(block, "items") / 100.0,
                net.bananemdnsa.historystages.data.logic.LogicBlockParams.percent(block, "xp") / 100.0);
    }

    /** "Time -50%, XP -25%": only the parts that change, joined by commas. */
    public static MutableComponent costSummary(double time, double items, double xp) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (var part : new Object[][] {
                {"gui.historystages.cost.time", time},
                {"gui.historystages.cost.items", items},
                {"gui.historystages.cost.xp", xp}}) {
            double factor = (double) part[1];
            if (Math.abs(factor - 1.0) < 1e-9) continue;
            if (!first) out.append(", ");
            out.append(Component.translatable((String) part[0], signedPercent(factor)));
            first = false;
        }
        return out;
    }

    /** -50 %, +25 %, or "free" for 0. */
    public static Component signedPercent(double factor) {
        if (factor <= 0.0) return Component.translatable("gui.historystages.cost.free");
        long delta = Math.round((factor - 1.0) * 100);
        return Component.literal((delta > 0 ? "+" : "−") + Math.abs(delta) + " %");
    }

    /**
     * The heading of a cost block's tooltip lines: "✦ Time -50%, solange:" when it holds, "✦ Would
     * be …" as a hint when it does not. Worded by the "cost" row of the tooltip layout when set,
     * with {@code %effect%} as the summary.
     */
    public static MutableComponent costHeader(LogicBlock block, boolean active) {
        MutableComponent summary = costSummary(block);
        String custom = templateText("cost");
        if (!custom.isEmpty() && active) {
            return Component.literal(custom.replace("%effect%", summary.getString()).replace('&', '§'));
        }
        return Component.translatable(active ? "gui.historystages.cost.header" : "gui.historystages.cost.hint", summary);
    }

    public static boolean showInactiveCostHints() {
        return Config.VISUAL.showInactiveCostHints.get();
    }

    // ---- revoke ----------------------------------------------------------------------

    /** The stage's own "revoke when" blocks, for "⚠ Revoked when …". Empty when switched off. */
    public static List<LogicBlock> revokeBlocks(String stageId, boolean individual) {
        if (!Config.VISUAL.showRevokeWarningOnStage.get()) return List.of();
        StageEntry entry = individual ? StageManager.getIndividualStages().get(stageId)
                : StageManager.getStages().get(stageId);
        if (entry == null || !entry.hasLogic()) return List.of();
        List<LogicBlock> out = new ArrayList<>();
        for (LogicBlock block : entry.getLogicBlocks()) {
            if (block.isType(net.bananemdnsa.historystages.data.logic.LogicBlockTypes.REVOKE_WHEN) && block.isUnderstood()) {
                out.add(block);
            }
        }
        return out;
    }

    /**
     * What unlocking this stage would take away from the local player: the stages they own whose
     * "revoke when" block would fire on that very unlock. Names, "???" where hidden. Empty when
     * switched off, or when the stage is already unlocked.
     */
    public static List<String> revokedByUnlocking(String stageId, boolean individual) {
        if (!Config.VISUAL.showRevokeWarningOnTrigger.get()) return List.of();
        var global = ClientStageStates.global();
        var own = ClientStageStates.individual();
        if ((individual ? own : global).isUnlocked(stageId)) return List.of();

        var globalAfter = individual ? global : StageLogic.withChange(global, stageId, true);
        var ownAfter = individual ? StageLogic.withChange(own, stageId, true) : own;
        List<String> out = new ArrayList<>();
        if (!individual) {
            for (var e : StageManager.getStages().entrySet()) {
                if (!e.getValue().hasLogic() || !global.isUnlocked(e.getKey())) continue;
                if (StageLogic.revokeFires(e.getValue().getLogicBlocks(), StageScope.GLOBAL,
                        global, net.bananemdnsa.historystages.api.stage.StageStateView.NONE_UNLOCKED,
                        globalAfter, net.bananemdnsa.historystages.api.stage.StageStateView.NONE_UNLOCKED)) {
                    out.add(displayName(e.getKey(), false));
                }
            }
        }
        for (var e : StageManager.getIndividualStages().entrySet()) {
            if (!e.getValue().hasLogic() || !own.isUnlocked(e.getKey())) continue;
            if (StageLogic.revokeFires(e.getValue().getLogicBlocks(), StageScope.INDIVIDUAL,
                    global, own, globalAfter, ownAfter)) {
                out.add(displayName(e.getKey(), true));
            }
        }
        return out;
    }

    /** "⚠ Revoked when:" — worded by the "revoke" row of the tooltip layout when set. */
    public static MutableComponent revokeHeader() {
        String custom = templateText("revoke");
        if (!custom.isEmpty()) return Component.literal(custom.replace('&', '§'));
        return Component.translatable("gui.historystages.revoke.header");
    }

    /** "⚠ Unlocking takes away:" — worded by the "revoke_trigger" row when set. */
    public static MutableComponent revokeTriggerHeader() {
        String custom = templateText("revoke_trigger");
        if (!custom.isEmpty()) return Component.literal(custom.replace('&', '§'));
        return Component.translatable("gui.historystages.revoke.trigger_header");
    }

    private static String stageName(String stageId, StageScope termScope, StageScope owner) {
        StageScope scope = termScope != null ? termScope : owner;
        // A condition naming a stage the player may not see yet names it as "???".
        return displayName(stageId, scope == StageScope.INDIVIDUAL);
    }

    /**
     * One reason line: either a condition on a stage, or the "One of:" line over an any-of group.
     *
     * @param unlocked for a term: true for "is unlocked", false for "is locked"
     * @param indent   0, or 1 under a "One of:" line
     */
    public record Line(boolean anyOfHeader, String stageName, boolean unlocked, int indent) {

        public static Line term(String stageName, boolean unlocked, int indent) {
            return new Line(false, stageName, unlocked, indent);
        }

        public static Line anyOf() {
            return new Line(true, "", false, 0);
        }

        /**
         * The layout row that words this line. Shared by every block type: a condition reads the
         * same whether it blocks a stage or, later, makes it cheaper. Only the heading is per type.
         */
        public String templateId() {
            return unlocked ? "logic.unlocked" : "logic.locked";
        }

        /**
         * The line's text, worded by the scroll tooltip layout when the pack changed it there,
         * so the pedestal and the graph say exactly what the tooltip says.
         */
        public MutableComponent text() {
            if (anyOfHeader) return Component.translatable("gui.historystages.blocked.any_of");
            String custom = templateText(templateId());
            if (!custom.isEmpty()) {
                return Component.literal(custom.replace("%stage%", stageName).replace('&', '§'));
            }
            return Component.translatable(unlocked
                    ? "gui.historystages.blocked.term_unlocked"
                    : "gui.historystages.blocked.term_not_unlocked", stageName);
        }
    }
}
