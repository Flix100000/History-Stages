package net.bananemdnsa.historystages.api.lock;

import net.bananemdnsa.historystages.util.lock.SlotLockMenu;
import net.bananemdnsa.historystages.util.lock.SlotLockState;
import net.bananemdnsa.historystages.util.lock.SlotLocks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.Optional;

/**
 * Slot locks, asked the way the mod asks them for itself: for inventory sorters that move items
 * without clicking, mods that draw their own slots, and scripts that put items somewhere.
 *
 * <p>Works on both sides. Pass the server player on the server; on the client, pass the local
 * player and the answer comes from the stages the client has been sent. Creative and spectator
 * players are never locked out, so every query answers {@link SlotState#OPEN} for them.
 *
 * <p>Player inventory slots are numbered as in {@code Inventory}: 0-8 hotbar, 9-35 main
 * inventory, 36-39 armor (feet to head), 40 offhand. They can be locked by global and individual
 * stages. Container slots are locked by global stages only and are numbered as the menu numbers
 * them.
 *
 * @since API 6.2
 */
public final class SlotLockQuery {

    private SlotLockQuery() {}

    /** The state of one of the player's own inventory slots. */
    public static SlotState state(Player player, int inventoryIndex) {
        return of(SlotLocks.playerSlot(player, inventoryIndex));
    }

    /**
     * The state of any slot of an open menu, as seen by {@code player}: the player's own slots
     * by inventory index, container slots by the menu's target.
     */
    public static SlotState state(Player player, Slot slot) {
        return of(SlotLocks.stateOf(slot, player));
    }

    /** Shorthand for {@code state(player, inventoryIndex).isLocked()}. */
    public static boolean isLocked(Player player, int inventoryIndex) {
        return state(player, inventoryIndex).isLocked();
    }

    /** Shorthand for {@code state(player, slot).isLocked()}. */
    public static boolean isLocked(Player player, Slot slot) {
        return state(player, slot).isLocked();
    }

    /**
     * A container slot without a player, for machines and automation. {@code target} is a block
     * id such as {@code minecraft:barrel}, or a menu type id for menus with no block behind them.
     * Server side only.
     */
    public static SlotState containerState(String target, int index) {
        return of(SlotLocks.containerSlot(null, target, index));
    }

    /**
     * Which target a menu's container slots answer to: the block it was opened from when that is
     * known, otherwise its menu type id. Empty for a menu that holds nothing but the player's own
     * inventory. On the client the block is known once the server has said so, shortly after the
     * menu opens; until then this is the menu type.
     */
    public static Optional<String> targetOf(AbstractContainerMenu menu) {
        return Optional.ofNullable(((SlotLockMenu) menu).historystages$slotTarget());
    }

    private static SlotState of(SlotLockState state) {
        return switch (state) {
            case OPEN -> SlotState.OPEN;
            case HIDDEN -> SlotState.HIDDEN;
            case OVERLAY -> SlotState.OVERLAY;
        };
    }
}
