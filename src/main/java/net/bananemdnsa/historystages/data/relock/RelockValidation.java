package net.bananemdnsa.historystages.data.relock;

import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.trigger.TriggerCondition;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageMode;
import net.bananemdnsa.historystages.data.auto.NegatedTrigger;
import net.bananemdnsa.historystages.data.auto.TriggerTypes;
import net.bananemdnsa.historystages.data.auto.conditions.UnknownTrigger;

import java.util.ArrayList;
import java.util.List;

/**
 * What is wrong with a stage's re-lock setup, as log lines. Nothing is removed: the runtime skips
 * what does not fit and the file keeps it, so a fix in the editor or a later addon update brings
 * it back without data loss.
 */
public final class RelockValidation {

    private RelockValidation() {}

    public static List<String> problems(String stageId, StageEntry entry, StageScope scope) {
        List<String> out = new ArrayList<>();
        StageMode mode = entry.getMode();

        if (entry.getAutoTrigger() != null) {
            for (String problem : entry.getAutoTrigger().getReadProblems()) {
                out.add("Stage '" + stageId + "' auto_trigger: " + problem);
            }
        }
        if (entry.getLockTrigger() != null) {
            for (String problem : entry.getLockTrigger().getReadProblems()) {
                out.add("Stage '" + stageId + "': " + problem);
            }
        }

        if ((mode == StageMode.AUTO || mode == StageMode.TEMPORARY) && entry.getAutoTrigger() != null) {
            for (TriggerCondition t : entry.getAutoTrigger().getTriggers()) {
                if (t instanceof NegatedTrigger) {
                    out.add("Stage '" + stageId + "' is mode=" + mode.serialize() + " and its '" + t.type()
                            + "' auto_trigger has negate=true. Negation only works in lock_trigger. Ignored, kept in the file.");
                }
            }
        }

        if (mode == StageMode.CONDITIONAL && entry.getAutoTrigger() != null) {
            for (TriggerCondition t : entry.getAutoTrigger().getTriggers()) {
                if (t instanceof UnknownTrigger) continue; // its mod is missing; reported elsewhere
                if (t instanceof NegatedTrigger) {
                    out.add("Stage '" + stageId + "' is mode=conditional and its '" + t.type()
                            + "' trigger has negate=true. Negation only works in lock_trigger. Ignored, kept in the file.");
                } else if (!TriggerRules.stateUsable(t.type(), scope)) {
                    out.add("Stage '" + stageId + "' is mode=conditional, but trigger type '" + t.type() + "' "
                            + (scope == StageScope.GLOBAL
                                ? "is not a world state (global conditional stages accept world states only, e.g. weather, world_time)."
                                : "is not a state that can stop being true.")
                            + " Ignored, kept in the file.");
                }
            }
        }

        if (mode == StageMode.CONDITIONAL && scope == StageScope.GLOBAL) {
            // getDependencies() never returns null.
            for (DependencyGroup g : entry.getDependencies()) {
                if (g.hasNonStageRequirements() || !g.getIndividualStages().isEmpty()) {
                    out.add("Global conditional stage '" + stageId + "' has dependencies that need a player "
                            + "(items, XP, kills, ...). A global conditional stage has no player to check them "
                            + "against, so they count as unmet and the stage will not open.");
                    break;
                }
            }
            if (!entry.getRecipes().isEmpty()) {
                out.add("Global conditional stage '" + stageId + "' locks recipes. Every time it opens or closes, "
                        + "the recipe list is resent to all players, and if the hidden recipes change, every "
                        + "datapack is reloaded. With states that flip often this can stall the server. "
                        + "Consider an individual stage, which never touches the recipe list.");
            }
        }

        LockTrigger lt = entry.getLockTrigger();
        if (lt != null && !lt.isEmpty()) {
            if (!mode.allowsLockTrigger()) {
                out.add("Stage '" + stageId + "' has a lock_trigger but mode=" + mode.serialize()
                        + ". lock_trigger only works on mode=default and mode=auto. Ignored, kept in the file.");
            } else {
                String rawMode = lt.getRawMode();
                if (rawMode != null && !rawMode.equalsIgnoreCase("any") && !rawMode.equalsIgnoreCase("all")) {
                    out.add("Stage '" + stageId + "' has lock_trigger.mode '" + rawMode
                            + "'. Expected 'any' or 'all'. Defaulting to 'any'.");
                }
                for (TriggerCondition t : lt.getTriggers()) {
                    if (!(t instanceof NegatedTrigger) && !TriggerTypes.scopesOf(t.type()).contains(scope)) {
                        out.add("Stage '" + stageId + "' has a lock trigger of type '" + t.type()
                                + "', which does not support " + (scope == StageScope.GLOBAL ? "global" : "individual")
                                + " stages. Ignored, kept in the file.");
                    } else if (t instanceof NegatedTrigger && !TriggerRules.stateUsable(t.type(), scope)) {
                        out.add("Stage '" + stageId + "' has a negated '" + t.type() + "' lock trigger. "
                                + (scope == StageScope.GLOBAL
                                    ? "Global stages can only negate world states (e.g. weather, world_time)."
                                    : "Only states can be negated; an event cannot stop happening.")
                                + " Ignored, kept in the file.");
                    }
                }
            }
        }
        return out;
    }
}
