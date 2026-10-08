package net.bananemdnsa.historystages.data.relock;

import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.stage.StageStates;
import net.bananemdnsa.historystages.api.trigger.StateView;
import net.bananemdnsa.historystages.api.trigger.TriggerCondition;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.auto.CombineMode;
import net.bananemdnsa.historystages.data.auto.NegatedTrigger;
import net.bananemdnsa.historystages.data.auto.TriggerTypes;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.LockTriggerProgressData;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Locks DEFAULT and AUTO stages again through their {@code lock_trigger}.
 *
 * <p>Plain lock triggers arrive through {@code AutoTriggerManager.process}, so every event source
 * (and every addon that fires its own trigger) reaches both lists without knowing about this one.
 * Negated triggers are polled from the shared {@link StateView} once a second.
 *
 * <p>A relock posts stage events, and their listeners may unlock or relock further stages while
 * one of the loops below is still running. The index lists are immutable and the negation sets
 * concurrent, so that can never trip an iteration.
 */
public final class LockTriggerManager {

    private LockTriggerManager() {}

    public record Indexed(String stageId, boolean isIndividual, TriggerCondition trigger) {}

    /** A stage one {@link #process} call locked, so the unlock pass of that same event skips it. */
    public record Relocked(String stageId, boolean isIndividual) {}

    private static final Map<String, List<Indexed>> BY_TYPE = new ConcurrentHashMap<>();
    private static final Set<String> NEGATED_GLOBAL = ConcurrentHashMap.newKeySet();
    private static final Set<String> NEGATED_INDIVIDUAL = ConcurrentHashMap.newKeySet();

    public static void rebuildIndex() {
        Map<String, List<Indexed>> byType = new HashMap<>();
        Set<String> negGlobal = new HashSet<>();
        Set<String> negIndividual = new HashSet<>();
        addFrom(StageManager.getStages(), false, byType, negGlobal);
        addFrom(StageManager.getIndividualStages(), true, byType, negIndividual);

        BY_TYPE.clear();
        byType.forEach((type, list) -> BY_TYPE.put(type, List.copyOf(list)));
        NEGATED_GLOBAL.clear();
        NEGATED_GLOBAL.addAll(negGlobal);
        NEGATED_INDIVIDUAL.clear();
        NEGATED_INDIVIDUAL.addAll(negIndividual);
    }

    private static void addFrom(Map<String, StageEntry> stages, boolean individual,
                                Map<String, List<Indexed>> byType, Set<String> negated) {
        StageScope scope = individual ? StageScope.INDIVIDUAL : StageScope.GLOBAL;
        for (var e : stages.entrySet()) {
            StageEntry se = e.getValue();
            LockTrigger lt = se.getLockTrigger();
            if (!se.getMode().allowsLockTrigger() || lt == null || lt.isEmpty()) continue;
            for (TriggerCondition t : lt.getTriggers()) {
                if (t instanceof NegatedTrigger) {
                    if (TriggerRules.stateUsable(t.type(), scope)) negated.add(e.getKey());
                    continue;
                }
                if (!TriggerTypes.scopesOf(t.type()).contains(scope)) continue;
                byType.computeIfAbsent(t.type(), k -> new ArrayList<>())
                        .add(new Indexed(e.getKey(), individual, t));
            }
        }
    }

    public static boolean hasType(String type) {
        List<Indexed> l = BY_TYPE.get(type);
        return l != null && !l.isEmpty();
    }

    public static boolean hasNegations() {
        return !NEGATED_GLOBAL.isEmpty() || !NEGATED_INDIVIDUAL.isEmpty();
    }

    public static Set<String> indexedStageIds() {
        Set<String> ids = new HashSet<>(NEGATED_GLOBAL);
        ids.addAll(NEGATED_INDIVIDUAL);
        BY_TYPE.values().forEach(l -> l.forEach(i -> ids.add(i.stageId())));
        return ids;
    }

    /**
     * Event path, called from AutoTriggerManager.process for every trigger type. Returns the stages
     * it locked; empty (and allocation-free) in the usual case where nothing did.
     */
    public static Set<Relocked> process(String type, Predicate<TriggerCondition> matcher, ServerPlayer player) {
        List<Indexed> candidates = BY_TYPE.get(type);
        if (candidates == null || candidates.isEmpty() || player == null) return Set.of();
        Set<Relocked> relocked = null;
        for (Indexed it : candidates) {
            if (matcher.test(it.trigger())
                    && record(it.stageId(), it.isIndividual(), it.trigger().signature(), player, player.serverLevel())) {
                if (relocked == null) relocked = new HashSet<>();
                relocked.add(new Relocked(it.stageId(), it.isIndividual()));
            }
        }
        return relocked == null ? Set.of() : relocked;
    }

    /** Negated individual triggers for one player, from that player's view. */
    public static void pollPlayer(ServerPlayer player, StateView view) {
        for (String stageId : NEGATED_INDIVIDUAL) {
            RelockPolls.guarded(stageId, true, () -> {
                StageEntry se = StageManager.getIndividualStages().get(stageId);
                if (se == null) return;
                for (NegatedTrigger n : StateEvaluation.firingNegations(se.getLockTrigger(), view, StageScope.INDIVIDUAL)) {
                    record(stageId, true, n.signature(), player, player.serverLevel());
                }
            });
        }
    }

    /** Negated global triggers (world states only), from the world view. */
    public static void pollWorld(ServerLevel overworld, StateView view) {
        for (String stageId : NEGATED_GLOBAL) {
            RelockPolls.guarded(stageId, false, () -> {
                StageEntry se = StageManager.getStages().get(stageId);
                if (se == null) return;
                for (NegatedTrigger n : StateEvaluation.firingNegations(se.getLockTrigger(), view, StageScope.GLOBAL)) {
                    record(stageId, false, n.signature(), null, overworld);
                }
            });
        }
    }

    /** Returns true if this call locked the stage. */
    private static boolean record(String stageId, boolean individual, long sig,
                                  @Nullable ServerPlayer player, ServerLevel level) {
        if (individual && player == null) return false;
        StageEntry se = (individual ? StageManager.getIndividualStages() : StageManager.getStages()).get(stageId);
        if (se == null || se.getLockTrigger() == null) return false;
        // Lock triggers only count while the stage is open.
        boolean open = individual
                ? IndividualStageData.get(level).hasStage(player.getUUID(), stageId)
                : StageData.SERVER_CACHE.contains(stageId);
        if (!open) return false;

        LockTriggerProgressData data = LockTriggerProgressData.get(level);
        Set<Long> set = individual ? data.individual(player.getUUID(), stageId) : data.global(stageId);
        if (!set.add(sig)) return false;
        data.touch();
        if (!satisfied(se.getLockTrigger(), set, individual ? StageScope.INDIVIDUAL : StageScope.GLOBAL)) return false;

        // Clear before relocking: relock fires StageEvents, and nothing should see stale progress.
        if (individual) data.clearIndividual(player.getUUID(), stageId); else data.clearGlobal(stageId);
        // Mark the loss first: a script answering the Locked event with an unlock must already
        // hit the lost gate.
        boolean lose = !se.getLockTrigger().isReUnlockable();
        if (lose) {
            if (individual) LostStages.markIndividual(stageId, player); else LostStages.markGlobal(stageId, level);
        }
        boolean locked = individual ? StageStates.relockIndividual(stageId, player)
                                    : StageStates.relockGlobal(stageId, level);
        if (!locked) {
            if (lose) {
                if (individual) LostStages.clearIndividual(stageId, player); else LostStages.clearGlobal(stageId, level);
            }
            String msg = "Lock trigger of '" + stageId + "' fired, but the relock did not go through.";
            if (individual) DebugLogger.runtime("Re-lock", player.getName().getString(), msg);
            else DebugLogger.runtime("Re-lock", msg);
        }
        return locked;
    }

    /** ALL counts only the triggers the index accepted; ignored ones must not pin the stage. */
    private static boolean satisfied(LockTrigger cfg, Set<Long> fired, StageScope scope) {
        if (cfg.resolvedMode() != CombineMode.ALL) return !fired.isEmpty();
        boolean any = false;
        for (TriggerCondition t : cfg.getTriggers()) {
            boolean usable = t instanceof NegatedTrigger
                    ? TriggerRules.stateUsable(t.type(), scope)
                    : TriggerTypes.scopesOf(t.type()).contains(scope);
            if (!usable) continue;
            any = true;
            if (!fired.contains(t.signature())) return false;
        }
        return any;
    }

    public static void clearProgress(String stageId, boolean individual, @Nullable UUID player, ServerLevel level) {
        LockTriggerProgressData data = LockTriggerProgressData.get(level);
        if (individual) { if (player != null) data.clearIndividual(player, stageId); }
        else data.clearGlobal(stageId);
    }

    public static void pruneOrphans(ServerLevel level) {
        LockTriggerProgressData.get(level).pruneOrphans(indexedStageIds());
    }
}
