package net.bananemdnsa.historystages.util.lock;

import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.stage.StageStateView;
import net.bananemdnsa.historystages.client.cache.ClientStageStates;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.lock.SlotLockEntry;
import net.bananemdnsa.historystages.data.lock.engine.StageLocks;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.List;

/**
 * The one place that answers "what is this slot for this player", on either side.
 *
 * <p>Creative players are never affected: creative inventory edits skip the slot checks anyway,
 * and a builder with half a hotbar gets nothing out of it.
 */
public final class SlotLocks {

    private SlotLocks() {}

    /**
     * The state of a menu slot. {@code viewer} may be null where the caller has none (mayPlace);
     * player inventory slots know their owner anyway.
     */
    public static SlotLockState stateOf(Slot slot, Player viewer) {
        if (!StageLocks.engine().anySlotLocks()) return SlotLockState.OPEN;
        if (slot.container instanceof Inventory inventory) {
            return playerSlot(inventory.player, slot.getContainerSlot());
        }
        AbstractContainerMenu menu = ((SlotMenuOwner) slot).historystages$menu();
        if (menu == null) return SlotLockState.OPEN;
        String target = ((SlotLockMenu) menu).historystages$slotTarget();
        if (target == null) return SlotLockState.OPEN;
        // The cheap answer first: most slots of most menus are named by no stage, and finding the
        // player means walking the menu's slots, which this runs for every slot every frame.
        if (StageLocks.engine().gatingStagesForSlot(target, slot.index, StageScope.GLOBAL).isEmpty()) {
            return SlotLockState.OPEN;
        }
        Player player = viewer;
        if (player == null) {
            Inventory inventory = SlotTargets.playerInventoryOf(menu);
            if (inventory != null) player = inventory.player;
        }
        return containerSlot(player, target, slot.index);
    }

    /**
     * Everything a renderer needs about one locked slot: its state, the entry that decides its
     * look and hint, and the stages gating it per scope for the "requires" lines.
     */
    public record Info(SlotLockState state, @org.jetbrains.annotations.Nullable SlotLockEntry entry,
                       List<String> globalGating, List<String> individualGating) {
        public static final Info OPEN = new Info(SlotLockState.OPEN, null, List.of(), List.of());
    }

    /** The full picture for a menu slot; only worth asking for slots that already block. */
    public static Info infoOf(Slot slot, Player viewer) {
        if (!StageLocks.engine().anySlotLocks()) return Info.OPEN;
        if (slot.container instanceof Inventory inventory) {
            return playerInfo(inventory.player, slot.getContainerSlot());
        }
        AbstractContainerMenu menu = ((SlotMenuOwner) slot).historystages$menu();
        if (menu == null) return Info.OPEN;
        String target = ((SlotLockMenu) menu).historystages$slotTarget();
        if (target == null || (viewer != null && (viewer.isCreative() || viewer.isSpectator()))) return Info.OPEN;
        return describe(target, slot.index, viewer, false);
    }

    public static Info playerInfo(Player player, int inventoryIndex) {
        if (player == null || player.isCreative() || player.isSpectator()) return Info.OPEN;
        if (!StageLocks.engine().anySlotLocks()) return Info.OPEN;
        return describe(SlotLockEntry.PLAYER, inventoryIndex, player, true);
    }

    private static Info describe(String target, int index, Player player, boolean withIndividual) {
        List<String> global = StageLocks.engine().gatingStagesForSlot(target, index, StageScope.GLOBAL);
        List<String> individual = withIndividual
                ? StageLocks.engine().gatingStagesForSlot(target, index, StageScope.INDIVIDUAL) : List.of();
        if (global.isEmpty() && individual.isEmpty()) return Info.OPEN;

        boolean client = player != null && player.level().isClientSide();
        StageStateView globalState = client ? ClientStageStates.global() : StageLocks.serverGlobal();
        StageStateView individualState = !withIndividual ? StageStateView.NONE_UNLOCKED
                : client ? ClientStageStates.individual() : StageLocks.serverIndividual(player.getUUID());
        SlotLockState state = SlotLockRules.resolve(global, globalState, individual, individualState,
                SlotLocks::entriesOf, target, index);
        if (state == SlotLockState.OPEN) return Info.OPEN;
        SlotLockEntry entry = SlotLockRules.governing(global, globalState, individual, individualState,
                SlotLocks::entriesOf, target, index);
        return new Info(state, entry, global, individual);
    }

    /** Cheap guard for the inventory search loops, which run on every pickup attempt. */
    public static boolean anyPlayerSlotLocks(Player player) {
        return player != null && !player.isCreative() && !player.isSpectator()
                && StageLocks.engine().anySlotLocks();
    }

    /** A slot of the player's own inventory: 0-8 hotbar, 9-35 main, 36-39 armor, 40 offhand. */
    public static SlotLockState playerSlot(Player player, int inventoryIndex) {
        if (player == null || player.isCreative() || player.isSpectator()) return SlotLockState.OPEN;
        if (!StageLocks.engine().anySlotLocks()) return SlotLockState.OPEN;

        String target = SlotLockEntry.PLAYER;
        List<String> global = StageLocks.engine().gatingStagesForSlot(target, inventoryIndex, StageScope.GLOBAL);
        List<String> individual = StageLocks.engine().gatingStagesForSlot(target, inventoryIndex, StageScope.INDIVIDUAL);
        if (global.isEmpty() && individual.isEmpty()) return SlotLockState.OPEN;

        boolean client = player.level().isClientSide();
        StageStateView globalState = client ? ClientStageStates.global() : StageLocks.serverGlobal();
        StageStateView individualState = client ? ClientStageStates.individual()
                : StageLocks.serverIndividual(player.getUUID());
        return SlotLockRules.resolve(global, globalState, individual, individualState,
                SlotLocks::entriesOf, target, inventoryIndex);
    }

    /** A container slot; global stages only, since a chest looks the same to everyone. */
    public static SlotLockState containerSlot(Player player, String target, int menuIndex) {
        if (target == null) return SlotLockState.OPEN;
        if (player != null && (player.isCreative() || player.isSpectator())) return SlotLockState.OPEN;
        return containerSlotFor(target, menuIndex, player != null && player.level().isClientSide());
    }

    /**
     * For item-handler wrappers: the player's inventory answers per player (mod "give" helpers),
     * anything else as automation.
     */
    public static boolean handlerSlotBlocked(net.minecraft.world.Container container, int index) {
        if (!StageLocks.engine().anySlotLocks()) return false;
        if (container instanceof Inventory inventory) return playerSlot(inventory.player, index).blocks();
        return automationBlocked(container, index);
    }

    /**
     * Whether automation may not touch this slot. Only the vanilla containers the spec names:
     * their container index is the menu index, so a lock picked in the GUI means the same slot.
     */
    public static boolean automationBlocked(net.minecraft.world.Container container, int index) {
        if (!StageLocks.engine().anySlotLocks()) return false;
        net.minecraft.world.Container half = container instanceof net.minecraft.world.CompoundContainer compound
                ? ((net.bananemdnsa.historystages.mixin.slotlock.CompoundContainerAccessor) compound).historystages$first()
                : container;
        if (!(half instanceof net.minecraft.world.level.block.entity.BlockEntity be)
                || !AUTOMATED_TYPES.contains(be.getType())) {
            return false;
        }
        return SlotTargets.blockOf(container)
                .map(target -> containerSlotFor(target, index, false).blocks())
                .orElse(false);
    }

    private static final java.util.Set<net.minecraft.world.level.block.entity.BlockEntityType<?>> AUTOMATED_TYPES = java.util.Set.of(
            net.minecraft.world.level.block.entity.BlockEntityTypes.CHEST,
            net.minecraft.world.level.block.entity.BlockEntityTypes.TRAPPED_CHEST,
            net.minecraft.world.level.block.entity.BlockEntityTypes.BARREL,
            net.minecraft.world.level.block.entity.BlockEntityTypes.SHULKER_BOX,
            net.minecraft.world.level.block.entity.BlockEntityTypes.HOPPER,
            net.minecraft.world.level.block.entity.BlockEntityTypes.DISPENSER,
            net.minecraft.world.level.block.entity.BlockEntityTypes.DROPPER,
            net.minecraft.world.level.block.entity.BlockEntityTypes.FURNACE,
            net.minecraft.world.level.block.entity.BlockEntityTypes.BLAST_FURNACE,
            net.minecraft.world.level.block.entity.BlockEntityTypes.SMOKER,
            net.minecraft.world.level.block.entity.BlockEntityTypes.BREWING_STAND);

    private static SlotLockState containerSlotFor(String target, int index, boolean client) {
        if (!StageLocks.engine().anySlotLocks()) return SlotLockState.OPEN;
        List<String> global = StageLocks.engine().gatingStagesForSlot(target, index, StageScope.GLOBAL);
        if (global.isEmpty()) return SlotLockState.OPEN;
        StageStateView state = client ? ClientStageStates.global() : StageLocks.serverGlobal();
        return SlotLockRules.resolve(global, state, List.of(), StageStateView.NONE_UNLOCKED,
                SlotLocks::entriesOf, target, index);
    }

    private static List<SlotLockEntry> entriesOf(String stageId) {
        StageEntry stage = StageManager.getStages().get(stageId);
        if (stage == null) stage = StageManager.getIndividualStages().get(stageId);
        return stage != null ? stage.getSlotLocks() : List.of();
    }
}
