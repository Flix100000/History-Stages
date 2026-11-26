package net.bananemdnsa.historystages.data.lock;

import net.bananemdnsa.historystages.data.StageEntry;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Load-time cleanup of a stage's slot locks. Pure, so it can be tested without a server; the
 * caller turns the returned problems into load messages.
 */
public final class SlotLockValidation {

    /** Highest player inventory index: 36 main + 4 armor + offhand. */
    public static final int MAX_PLAYER_SLOT = 40;

    private SlotLockValidation() {}

    public static List<String> clean(String stageId, StageEntry stage, boolean individual) {
        List<String> problems = new ArrayList<>();
        List<SlotLockEntry> kept = new ArrayList<>();

        for (SlotLockEntry entry : stage.getSlotLocks()) {
            if (individual && !entry.isPlayerTarget()) {
                // Two players looking at the same chest cannot see it differently, and a hopper
                // has no player at all, so container slots only make sense globally.
                problems.add("Individual stage '" + stageId + "' locks slots of '" + entry.target()
                        + "' — container slot locks are only supported for global stages. Removed.");
                continue;
            }

            LinkedHashSet<Integer> valid = new LinkedHashSet<>();
            for (int slot : entry.slots()) {
                boolean ok = slot >= 0 && (!entry.isPlayerTarget() || slot <= MAX_PLAYER_SLOT);
                if (ok) {
                    valid.add(slot);
                } else {
                    problems.add("Stage '" + stageId + "' locks slot " + slot + " of '" + entry.target()
                            + "', which does not exist. Ignored.");
                }
            }
            if (valid.isEmpty()) continue;

            entry.setSlots(new ArrayList<>(valid));
            kept.add(entry);
        }

        stage.setSlotLocks(kept);
        return problems;
    }
}
