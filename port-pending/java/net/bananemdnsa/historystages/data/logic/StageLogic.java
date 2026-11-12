package net.bananemdnsa.historystages.data.logic;

import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.stage.StageStateView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Answers the one question round 1 of stage logic asks: is this stage blocked for this viewer,
 * and by which blocks.
 *
 * <p>Nothing is stored. "Blocked" is worked out from what is unlocked every time it is asked, so
 * the order things happen in does not matter and a stage can never stay blocked after its reason
 * went away — a relocked stage, a temporary stage running out, a stage lost on death.
 *
 * <p>Blocked only means "cannot be unlocked right now". A stage that is already unlocked is not
 * touched by it; the caller only asks about stages it is about to unlock or show as locked.
 */
public final class StageLogic {

    private StageLogic() {}

    public static Blocked blocked(List<LogicBlock> blocks, StageScope owner,
                                  StageStateView global, StageStateView individual) {
        if (blocks.isEmpty()) return Blocked.NONE;
        List<LogicBlock> matching = null;
        for (LogicBlock block : blocks) {
            if (!block.isType(LogicBlockTypes.BLOCKED_WHILE) || !block.isUnderstood()) continue;
            if (Condition.evaluate(block.condition(), owner, global, individual)) {
                if (matching == null) matching = new ArrayList<>(1);
                matching.add(block);
            }
        }
        return matching == null ? Blocked.NONE : new Blocked(List.copyOf(matching));
    }

    /** How much of a stage a viewer gets to see. */
    public enum Visibility { VISIBLE, ANONYMOUS, VANISH }

    /**
     * Whether the stage is hidden for this viewer, and how. Several holding {@code hidden_while}
     * blocks combine to the strongest: vanish beats anonymous.
     */
    public static Visibility visibility(List<LogicBlock> blocks, StageScope owner,
                                        StageStateView global, StageStateView individual) {
        Visibility result = Visibility.VISIBLE;
        for (LogicBlock block : blocks) {
            if (!block.isType(LogicBlockTypes.HIDDEN_WHILE) || !block.isUnderstood()) continue;
            if (!Condition.evaluate(block.condition(), owner, global, individual)) continue;
            if (LogicBlockParams.hiddenMode(block) == LogicBlockParams.HiddenMode.VANISH) return Visibility.VANISH;
            result = Visibility.ANONYMOUS;
        }
        return result;
    }

    /**
     * The cost factors in effect: every holding {@code cost_while} block multiplied together.
     * {@code inactive} lists the blocks whose condition does not hold yet, for "would be cheaper"
     * hints.
     */
    public static CostFactors cost(List<LogicBlock> blocks, StageScope owner,
                                   StageStateView global, StageStateView individual) {
        double time = 1.0;
        double items = 1.0;
        double xp = 1.0;
        List<LogicBlock> active = new ArrayList<>();
        List<LogicBlock> inactive = new ArrayList<>();
        for (LogicBlock block : blocks) {
            if (!block.isType(LogicBlockTypes.COST_WHILE) || !block.isUnderstood()) continue;
            if (Condition.evaluate(block.condition(), owner, global, individual)) {
                time *= LogicBlockParams.percent(block, LogicBlockParams.TIME) / 100.0;
                items *= LogicBlockParams.percent(block, LogicBlockParams.ITEMS) / 100.0;
                xp *= LogicBlockParams.percent(block, LogicBlockParams.XP) / 100.0;
                active.add(block);
            } else {
                inactive.add(block);
            }
        }
        if (active.isEmpty() && inactive.isEmpty()) return CostFactors.NONE;
        return new CostFactors(time, items, xp, List.copyOf(active), List.copyOf(inactive));
    }

    /** Multipliers on research time, item counts and XP levels; 1.0 each means unchanged. */
    public record CostFactors(double time, double items, double xp,
                              List<LogicBlock> active, List<LogicBlock> inactive) {
        public static final CostFactors NONE = new CostFactors(1.0, 1.0, 1.0, List.of(), List.of());

        public boolean isNeutral() {
            return time == 1.0 && items == 1.0 && xp == 1.0;
        }
    }

    /**
     * Whether a {@code revoke_when} block fires for this change: its condition was false with the
     * state before and is true with the state after. Once only — a condition that already held
     * does not fire again, which is what lets the stage be researched anew afterwards.
     */
    public static boolean revokeFires(List<LogicBlock> blocks, StageScope owner,
                                      StageStateView globalBefore, StageStateView individualBefore,
                                      StageStateView globalAfter, StageStateView individualAfter) {
        for (LogicBlock block : blocks) {
            if (!block.isType(LogicBlockTypes.REVOKE_WHEN) || !block.isUnderstood()) continue;
            boolean before = Condition.evaluate(block.condition(), owner, globalBefore, individualBefore);
            boolean after = Condition.evaluate(block.condition(), owner, globalAfter, individualAfter);
            if (!before && after) return true;
        }
        return false;
    }

    /**
     * The stages of scope {@code of} that the revoke blocks look at, for a stage owned in
     * {@code owner}'s scope. The change handler only has to look at stages whose revoke blocks
     * mention the stage that changed.
     */
    public static List<String> revokeReferences(List<LogicBlock> blocks, StageScope owner, StageScope of) {
        Set<String> ids = new LinkedHashSet<>();
        for (LogicBlock block : blocks) {
            if (!block.isType(LogicBlockTypes.REVOKE_WHEN) || !block.isUnderstood()) continue;
            Condition.forEachTerm(block.condition(), term -> {
                StageScope scope = term.scope() != null ? term.scope() : owner;
                if (scope == of) ids.add(term.stageId());
            });
        }
        return List.copyOf(ids);
    }

    /** {@code base} with one stage switched to {@code unlocked}; everything else as in {@code base}. */
    public static StageStateView withChange(StageStateView base, String stageId, boolean unlocked) {
        return id -> id.equals(stageId) ? unlocked : base.isUnlocked(id);
    }

    /** Every stage id the blocks mention, in order, each once. */
    public static List<String> referencedStages(List<LogicBlock> blocks) {
        Set<String> ids = new LinkedHashSet<>();
        for (LogicBlock block : blocks) {
            Condition.forEachTerm(block.condition(), term -> ids.add(term.stageId()));
        }
        return List.copyOf(ids);
    }

    /** The blocks holding a stage back, in file order. Empty when it is free to unlock. */
    public record Blocked(List<LogicBlock> matching) {
        public static final Blocked NONE = new Blocked(List.of());

        public boolean isBlocked() {
            return !matching.isEmpty();
        }

        public boolean isEmpty() {
            return matching.isEmpty();
        }
    }
}
