package net.bananemdnsa.historystages.data.relock;

import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.stage.StageStates;
import net.bananemdnsa.historystages.api.trigger.StateView;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StageMode;
import net.bananemdnsa.historystages.data.dependency.DependencyChecker;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.bananemdnsa.historystages.events.StateCapture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Opens conditional stages while their states hold and closes them when they stop. Nothing is
 * stored: whether the stage should be open can be read off the world at any time.
 *
 * <p>Opening goes through {@link StageStates#unlockGlobal}/{@code unlockIndividual}, so a
 * {@code blocked_while} rule holds it shut. Dependencies are checked only at that moment, as
 * agreed in the spec: losing one while the stage is open does not close it.
 *
 * <p>An admin lock or unlock on a conditional stage is overwritten at the next poll. That is the
 * mode doing its job; the wiki says so.
 */
public final class ConditionalStageManager {

    private ConditionalStageManager() {}

    // Copy-on-write: opening or closing posts stage events, and a revoke_when listener may change
    // other stages while these lists are being walked.
    private static final List<String> GLOBAL = new CopyOnWriteArrayList<>();
    private static final List<String> INDIVIDUAL = new CopyOnWriteArrayList<>();

    public static void rebuildIndex() {
        GLOBAL.clear();
        INDIVIDUAL.clear();
        RelockPolls.resetReported();
        StageManager.getStages().forEach((id, e) -> { if (e.getMode() == StageMode.CONDITIONAL) GLOBAL.add(id); });
        StageManager.getIndividualStages().forEach((id, e) -> { if (e.getMode() == StageMode.CONDITIONAL) INDIVIDUAL.add(id); });
    }

    public static boolean hasAny() { return !GLOBAL.isEmpty() || !INDIVIDUAL.isEmpty(); }

    public static void pollPlayer(ServerPlayer player, StateView view) {
        ServerLevel level = player.serverLevel();
        IndividualStageData data = IndividualStageData.get(level);
        for (String id : INDIVIDUAL) {
            RelockPolls.guarded(id, true, () -> pollPlayerStage(id, player, level, data, view));
        }
    }

    private static void pollPlayerStage(String id, ServerPlayer player, ServerLevel level,
                                        IndividualStageData data, StateView view) {
        StageEntry e = StageManager.getIndividualStages().get(id);
        if (e == null) return;
        boolean holds = StateEvaluation.conditionHolds(e.getAutoTrigger(), view, StageScope.INDIVIDUAL);
        boolean open = data.hasStage(player.getUUID(), id);
        if (holds && !open) {
            if (!e.hasDependencies() || DependencyChecker.checkAll(e, player, level,
                    StageScope.INDIVIDUAL, null).isFulfilled()) {
                StageStates.unlockIndividual(id, player);
            }
        } else if (!holds && open) {
            StageStates.relockIndividual(id, player);
        }
    }

    public static void pollWorld(ServerLevel overworld, StateView view) {
        for (String id : GLOBAL) {
            RelockPolls.guarded(id, false, () -> pollWorldStage(id, overworld, view));
        }
    }

    private static void pollWorldStage(String id, ServerLevel overworld, StateView view) {
        StageEntry e = StageManager.getStages().get(id);
        if (e == null) return;
        boolean holds = StateEvaluation.conditionHolds(e.getAutoTrigger(), view, StageScope.GLOBAL);
        boolean open = StageData.SERVER_CACHE.contains(id);
        if (holds && !open) {
            if (globalDependenciesMet(e, overworld)) StageStates.unlockGlobal(id, overworld);
        } else if (!holds && open) {
            StageStates.relockGlobal(id, overworld);
        }
    }

    /**
     * A global conditional stage has no player, so only stage dependencies can be checked. Any
     * dependency that needs a player counts as unmet (and was warned about at load). With that
     * ruled out, every built-in requirement checkAll asks either has nothing to look at or, like
     * the stage requirement, copes with a null player as the Requirement API promises.
     */
    private static boolean globalDependenciesMet(StageEntry e, ServerLevel level) {
        if (!e.hasDependencies()) return true;
        for (DependencyGroup g : e.getDependencies()) {
            if (g.hasNonStageRequirements() || !g.getIndividualStages().isEmpty()) return false;
        }
        return DependencyChecker.checkAll(e, null, level, StageScope.GLOBAL, null).isFulfilled();
    }

    /** One immediate pass for a player, used at login. */
    public static void evaluateNow(ServerPlayer player) {
        if (INDIVIDUAL.isEmpty()) return;
        pollPlayer(player, StateCapture.forPlayer(player));
    }
}
