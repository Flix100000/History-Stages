package net.bananemdnsa.historystages.data.relock;

import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.trigger.TriggerKind;
import net.bananemdnsa.historystages.data.auto.TriggerTypes;

/**
 * Where a trigger type may be used as a state: inside a conditional stage, or negated in a lock
 * list. Both follow one rule, so they live in one place.
 */
public final class TriggerRules {

    private TriggerRules() {}

    /**
     * Individual stages take any state. Global stages take world states only: "while someone is
     * in the Nether" would open a stage for everybody the moment one player steps through.
     */
    public static boolean stateUsable(String type, StageScope scope) {
        TriggerKind kind = TriggerTypes.kindOf(type);
        return scope == StageScope.GLOBAL ? kind == TriggerKind.WORLD_STATE : kind.isState();
    }
}
