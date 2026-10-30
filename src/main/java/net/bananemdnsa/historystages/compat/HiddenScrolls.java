package net.bananemdnsa.historystages.compat;

import net.bananemdnsa.historystages.client.cache.ClientStageStates;
import net.bananemdnsa.historystages.client.display.BlockedLines;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.logic.LogicBlock;
import net.bananemdnsa.historystages.data.logic.LogicBlockTypes;
import net.bananemdnsa.historystages.data.logic.StageLogic;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Which research scrolls the recipe viewers must not list: those of stages a "hidden while" block
 * currently hides from the local player. A scroll showing up in JEI would give the secret away.
 *
 * <p>Both hidden modes count — "???" hides the name in the graph, but a scroll in the item list
 * carries the name on it. A stage the player already has is never hidden.
 */
public final class HiddenScrolls {

    private HiddenScrolls() {}

    /** Whether any stage has a hidden block at all; nothing to do (or reload) otherwise. */
    public static boolean anyHiddenBlocks() {
        return anyHidden(StageManager.getStages()) || anyHidden(StageManager.getIndividualStages());
    }

    private static boolean anyHidden(Map<String, StageEntry> stages) {
        for (StageEntry entry : stages.values()) {
            if (!entry.hasLogic()) continue;
            for (LogicBlock block : entry.getLogicBlocks()) {
                if (block.isType(LogicBlockTypes.HIDDEN_WHILE)) return true;
            }
        }
        return false;
    }

    /** The stage ids whose scrolls are hidden right now. */
    public static Set<String> hiddenStageIds() {
        Set<String> out = new HashSet<>();
        collect(StageManager.getStages(), false, out);
        collect(StageManager.getIndividualStages(), true, out);
        return out;
    }

    private static void collect(Map<String, StageEntry> stages, boolean individual, Set<String> out) {
        for (Map.Entry<String, StageEntry> e : stages.entrySet()) {
            if (e.getValue().hasLogic() && isHidden(e.getKey(), individual)) out.add(e.getKey());
        }
    }

    public static boolean isHiddenScroll(ItemStack stack) {
        String stageId = ScrollVariants.readStageResearch(stack);
        if (stageId == null) return false;
        return isHidden(stageId, StageManager.isIndividualStage(stageId));
    }

    private static boolean isHidden(String stageId, boolean individual) {
        boolean unlocked = (individual ? ClientStageStates.individual() : ClientStageStates.global()).isUnlocked(stageId);
        if (unlocked) return false;
        return BlockedLines.visibilityForLocalPlayer(stageId, individual) != StageLogic.Visibility.VISIBLE;
    }
}
