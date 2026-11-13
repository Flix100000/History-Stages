package net.bananemdnsa.historystages.api.stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.bananemdnsa.historystages.api.trigger.TriggerCondition;
import net.bananemdnsa.historystages.block.entity.ResearchPedestalBlockEntity;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.auto.AutoTrigger;
import net.bananemdnsa.historystages.data.auto.CombineMode;
import net.bananemdnsa.historystages.data.auto.NegatedTrigger;
import net.bananemdnsa.historystages.data.saveddata.AutoTriggerGlobalData;
import net.bananemdnsa.historystages.data.saveddata.AutoTriggerProgressData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/**
 * How far along a stage is: its auto-trigger, and research running at a pedestal or stored on a
 * scroll. For progress bars, HUDs and quest books.
 */
public final class StageProgress {

    private StageProgress() {}

    /**
     * An auto-trigger's state for one viewer.
     *
     * @param all      true when every trigger has to fire, false when any one will do
     * @param entries  each trigger and whether it has fired. Negated triggers ("while not …")
     *                 are left out: they are checked on the spot and never recorded
     * @param complete true when the stage would unlock now as far as its triggers go
     */
    public record Triggers(boolean all, List<TriggerEntry> entries, boolean complete) {

        /** How many of {@link #entries} have fired. */
        public int met() {
            int n = 0;
            for (TriggerEntry e : entries) if (e.met()) n++;
            return n;
        }
    }

    /** One trigger of an auto-trigger and whether it has fired for this viewer. */
    public record TriggerEntry(TriggerCondition trigger, boolean met) {}

    /**
     * Research at a pedestal or on a scroll.
     *
     * @param stageId  the stage being researched
     * @param progress ticks done
     * @param total    ticks needed
     * @param running  true while the pedestal is actually counting; false when paused, when
     *                 requirements are missing, or for a scroll lying in an inventory
     * @param owner    who started it, or null if nobody has yet
     */
    public record Research(String stageId, int progress, int total, boolean running, @Nullable UUID owner) {

        /** Done so far, from 0 to 1. */
        public double fraction() {
            return total <= 0 ? 0 : Math.min(1.0, (double) progress / total);
        }
    }

    /**
     * The auto-trigger progress of a stage: the world's for a global stage, this player's for an
     * individual one. Empty for an unknown id or a stage without an auto-trigger.
     */
    public static Optional<Triggers> triggers(String stageId, ServerPlayer player) {
        StageScope scope = StageStates.scopeOf(stageId);
        if (scope == null) return Optional.empty();
        StageEntry entry = StageStates.findStage(stageId);
        AutoTrigger trigger = entry.getAutoTrigger();
        if (trigger == null || trigger.isEmpty()) return Optional.empty();

        Set<Long> fired = scope == StageScope.GLOBAL
                ? AutoTriggerGlobalData.get(player.serverLevel()).peek(stageId)
                : AutoTriggerProgressData.get(player.serverLevel()).peek(player.getUUID(), stageId);
        List<TriggerEntry> entries = new ArrayList<>();
        for (TriggerCondition condition : trigger.getTriggers()) {
            if (condition instanceof NegatedTrigger) continue;
            entries.add(new TriggerEntry(condition, fired != null && fired.contains(condition.signature())));
        }
        boolean all = trigger.resolvedMode() == CombineMode.ALL;
        boolean anyMet = entries.stream().anyMatch(TriggerEntry::met);
        boolean allMet = entries.stream().allMatch(TriggerEntry::met);
        return Optional.of(new Triggers(all, List.copyOf(entries), all ? allMet : anyMet));
    }

    /** What the pedestal at {@code pos} is researching. Empty if there is no pedestal or no scroll in it. */
    public static Optional<Research> research(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ResearchPedestalBlockEntity pedestal)) return Optional.empty();
        return Optional.ofNullable(pedestal.researchProgress());
    }

    /**
     * The research stored on a scroll: what a pedestal would pick up again. Empty for anything
     * that is not a research scroll.
     */
    public static Optional<Research> research(ItemStack scroll) {
        if (scroll == null || scroll.isEmpty()) return Optional.empty();
        CompoundTag tag = scroll.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains("StageResearch")) return Optional.empty();
        String stageId = tag.getString("StageResearch");
        int total = StageManager.isIndividualStage(stageId)
                ? StageManager.getIndividualResearchTimeInTicks(stageId)
                : StageManager.getResearchTimeInTicks(stageId);
        return Optional.of(new Research(stageId, tag.getInt("ResearchProgress"), total, false, null));
    }
}
