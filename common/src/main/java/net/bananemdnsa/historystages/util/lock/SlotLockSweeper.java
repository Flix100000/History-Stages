package net.bananemdnsa.historystages.util.lock;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.data.lock.SlotLockValidation;
import net.bananemdnsa.historystages.data.lock.engine.StageLocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Empties player slots that are locked but still hold something.
 *
 * <p>Stateless on purpose: it simply looks, so it covers every way a slot can end up locked with
 * an item in it (a relock, a new lock saved in the editor, a pack added to an existing world, a
 * relock while the player was offline) without remembering any of them. Container slots are never
 * touched: a chest is shared, and what lies in it is not this player's.
 */
public final class SlotLockSweeper {

    private SlotLockSweeper() {}

    public static void sweep(ServerPlayer player) {
        if (!Config.GAMEPLAY.dropItemsFromLockedSlots.get()) return;
        if (!SlotLocks.anyPlayerSlotLocks(player)) return;

        Inventory inventory = player.getInventory();
        boolean dropped = false;
        for (int i = 0; i <= SlotLockValidation.MAX_PLAYER_SLOT && i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !SlotLocks.playerSlot(player, i).blocks()) continue;
            inventory.setItem(i, ItemStack.EMPTY);
            player.drop(stack, false);
            dropped = true;
        }
        if (dropped) player.containerMenu.broadcastChanges();
    }

    public static void sweepAll(MinecraftServer server) {
        if (server == null || !StageLocks.engine().anySlotLocks()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sweep(player);
    }
}
