package net.bananemdnsa.historystages.data.relock;

import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.trigger.StateTrigger;
import net.bananemdnsa.historystages.api.trigger.StateView;
import net.bananemdnsa.historystages.api.trigger.TriggerCondition;
import net.bananemdnsa.historystages.data.auto.AutoTrigger;
import net.bananemdnsa.historystages.data.auto.CombineMode;
import net.bananemdnsa.historystages.data.auto.NegatedTrigger;

import java.util.ArrayList;
import java.util.List;

/** Pure checks over a {@link StateView}; the managers decide what to do with the answer. */
public final class StateEvaluation {

    private StateEvaluation() {}

    /**
     * Whether a conditional stage should be open. Only usable states count; anything else was
     * warned about at load and is skipped, so it can neither open the stage (ANY) nor pin it
     * shut (ALL).
     */
    public static boolean conditionHolds(AutoTrigger cfg, StateView view, StageScope scope) {
        if (cfg == null || cfg.isEmpty()) return false;
        boolean all = cfg.resolvedMode() == CombineMode.ALL;
        int usable = 0;
        for (TriggerCondition t : cfg.getTriggers()) {
            if (!(t instanceof StateTrigger st) || !TriggerRules.stateUsable(t.type(), scope)) continue;
            usable++;
            boolean holds = st.holds(view);
            if (all && !holds) return false;
            if (!all && holds) return true;
        }
        return all && usable > 0;
    }

    /** The negated lock triggers that fire in this view right now (usable ones only). */
    public static List<NegatedTrigger> firingNegations(LockTrigger cfg, StateView view, StageScope scope) {
        List<NegatedTrigger> out = new ArrayList<>();
        if (cfg == null || cfg.isEmpty()) return out;
        for (TriggerCondition t : cfg.getTriggers()) {
            if (t instanceof NegatedTrigger n
                    && TriggerRules.stateUsable(n.type(), scope)
                    && n.holds(view)) {
                out.add(n);
            }
        }
        return out;
    }
}
