package net.bananemdnsa.historystages.data.logic;

import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.stage.StageStateView;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.lock.engine.StageLocks;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Server-side view of {@link StageLogic}: whether a stage may be unlocked right now, whether a
 * player may see it, and what researching it costs them.
 *
 * <p>Every unlock path that is not an admin override asks {@link #global}/{@link #individual}
 * first — {@code StageStates}, the research pedestal and the FTB Quests reward. A new path that
 * writes the saved data directly without coming here is what {@code UnlockGateGuardTest} exists
 * to catch.
 */
public final class StageLogicGate {

    /** What a stage the viewer may not see is called wherever it still has to be mentioned. */
    public static final String HIDDEN_NAME = "???";

    private StageLogicGate() {}

    public static StageLogic.Blocked global(String stageId) {
        StageEntry entry = StageManager.getStages().get(stageId);
        if (entry == null || !entry.hasLogic()) return StageLogic.Blocked.NONE;
        return StageLogic.blocked(entry.getLogicBlocks(), StageScope.GLOBAL,
                StageLocks.serverGlobal(), StageStateView.NONE_UNLOCKED);
    }

    public static StageLogic.Blocked individual(String stageId, UUID player) {
        StageEntry entry = StageManager.getIndividualStages().get(stageId);
        if (entry == null || !entry.hasLogic()) return StageLogic.Blocked.NONE;
        return StageLogic.blocked(entry.getLogicBlocks(), StageScope.INDIVIDUAL,
                StageLocks.serverGlobal(), individualState(player));
    }

    /** Either scope, by where the stage is defined. */
    public static StageLogic.Blocked of(String stageId, boolean individual, UUID player) {
        return individual ? individual(stageId, player) : global(stageId);
    }

    /** Whether {@code viewer} may see the stage. A null viewer sees what nobody in particular sees. */
    public static StageLogic.Visibility visibility(String stageId, boolean individual, @Nullable UUID viewer) {
        StageEntry entry = entry(stageId, individual);
        if (entry == null || !entry.hasLogic()) return StageLogic.Visibility.VISIBLE;
        return StageLogic.visibility(entry.getLogicBlocks(), scope(individual),
                StageLocks.serverGlobal(), individualState(viewer));
    }

    /**
     * The name to show {@code viewer} for this stage: its display name, or "???" while hidden.
     * A stage the viewer already has is never hidden from them.
     */
    public static String nameFor(String stageId, boolean individual, @Nullable UUID viewer) {
        StageEntry entry = entry(stageId, individual);
        String name = entry != null ? entry.getDisplayName() : stageId;
        StageStateView owned = individual ? individualState(viewer) : StageLocks.serverGlobal();
        if (owned.isUnlocked(stageId)) return name;
        return visibility(stageId, individual, viewer) == StageLogic.Visibility.VISIBLE ? name : HIDDEN_NAME;
    }

    /**
     * {@link #nameFor} for an id whose scope the caller does not track: the global tree is asked
     * first, the way the lock messages have always looked names up.
     */
    public static String nameForAnyScope(String stageId, @Nullable UUID viewer) {
        return nameFor(stageId, !StageManager.getStages().containsKey(stageId)
                && StageManager.getIndividualStages().containsKey(stageId), viewer);
    }

    /** Names for a list of stage ids of one scope, in order, hidden ones as "???". */
    public static List<String> namesFor(List<String> stageIds, boolean individual, @Nullable UUID viewer) {
        return stageIds.stream().map(id -> nameFor(id, individual, viewer)).toList();
    }

    /** What researching the stage costs {@code player} right now, from its cost blocks. */
    public static StageLogic.CostFactors cost(String stageId, boolean individual, @Nullable UUID player) {
        StageEntry entry = entry(stageId, individual);
        if (entry == null || !entry.hasLogic()) return StageLogic.CostFactors.NONE;
        return StageLogic.cost(entry.getLogicBlocks(), scope(individual),
                StageLocks.serverGlobal(), individualState(player));
    }

    @Nullable
    private static StageEntry entry(String stageId, boolean individual) {
        return individual ? StageManager.getIndividualStages().get(stageId) : StageManager.getStages().get(stageId);
    }

    private static StageScope scope(boolean individual) {
        return individual ? StageScope.INDIVIDUAL : StageScope.GLOBAL;
    }

    private static StageStateView individualState(@Nullable UUID player) {
        return player == null ? StageStateView.NONE_UNLOCKED : StageLocks.serverIndividual(player);
    }
}
