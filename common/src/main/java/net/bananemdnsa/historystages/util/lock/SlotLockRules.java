package net.bananemdnsa.historystages.util.lock;

import net.bananemdnsa.historystages.api.stage.StageStateView;
import net.bananemdnsa.historystages.data.lock.SlotLockEntry;
import net.bananemdnsa.historystages.data.lock.SlotLockMode;
import net.bananemdnsa.historystages.data.lock.engine.LockResolution;

import java.util.List;
import java.util.function.Function;

/** The slot answer without Minecraft in it, so it can be tested on its own. */
public final class SlotLockRules {

    private SlotLockRules() {}

    public static SlotLockState resolve(List<String> globalGating, StageStateView global,
                                        List<String> individualGating, StageStateView individual,
                                        Function<String, List<SlotLockEntry>> entriesOfStage,
                                        String target, int slot) {
        if (globalGating.isEmpty() && individualGating.isEmpty()) return SlotLockState.OPEN;
        if (!LockResolution.isLocked(globalGating, global, individualGating, individual)) {
            return SlotLockState.OPEN;
        }
        // Hide wins: a pack that hides a slot in one stage would look broken if another stage
        // brought the frame back just to put a lock on it.
        if (anyHides(globalGating, entriesOfStage, target, slot)
                || anyHides(individualGating, entriesOfStage, target, slot)) {
            return SlotLockState.HIDDEN;
        }
        return SlotLockState.OVERLAY;
    }

    /**
     * The entry whose look and hint the slot shows. Among entries with the winning mode, from
     * stages that still lock the slot (or any gating stage, when only an interchangeable group
     * keeps it locked): one with its own look or hint beats a plain one, so a stage that merely
     * locks cannot cover a look another stage styled; a tie goes to the stage id that sorts
     * first, because the engine's order comes from a hash map. Null when the slot is open.
     */
    public static SlotLockEntry governing(List<String> globalGating, StageStateView global,
                                          List<String> individualGating, StageStateView individual,
                                          Function<String, List<SlotLockEntry>> entriesOfStage,
                                          String target, int slot) {
        SlotLockState state = resolve(globalGating, global, individualGating, individual, entriesOfStage, target, slot);
        if (state == SlotLockState.OPEN) return null;
        SlotLockMode wanted = state == SlotLockState.HIDDEN ? SlotLockMode.HIDE : SlotLockMode.OVERLAY;

        List<Candidate> locked = new java.util.ArrayList<>();
        List<Candidate> all = new java.util.ArrayList<>();
        for (int pass = 0; pass < 2; pass++) {
            List<String> stages = pass == 0 ? globalGating : individualGating;
            StageStateView view = pass == 0 ? global : individual;
            for (String stageId : stages) {
                for (SlotLockEntry entry : entriesOfStage.apply(stageId)) {
                    if (entry.mode() != wanted || !entry.target().equals(target) || !entry.slots().contains(slot)) continue;
                    Candidate candidate = new Candidate(stageId, entry);
                    all.add(candidate);
                    if (!view.isUnlocked(stageId)) locked.add(candidate);
                }
            }
        }
        List<Candidate> pool = locked.isEmpty() ? all : locked;
        return pool.stream().min(java.util.Comparator
                        .comparing((Candidate c) -> !customised(c.entry()))
                        .thenComparing(Candidate::stageId))
                .map(Candidate::entry)
                .orElse(null);
    }

    private record Candidate(String stageId, SlotLockEntry entry) {}

    private static boolean customised(SlotLockEntry entry) {
        return !entry.look().isDefault() || !entry.hint().isEmpty();
    }

    private static boolean anyHides(List<String> stageIds, Function<String, List<SlotLockEntry>> entriesOfStage,
                                    String target, int slot) {
        for (String stageId : stageIds) {
            for (SlotLockEntry entry : entriesOfStage.apply(stageId)) {
                if (entry.mode() == SlotLockMode.HIDE && entry.target().equals(target)
                        && entry.slots().contains(slot)) {
                    return true;
                }
            }
        }
        return false;
    }
}
