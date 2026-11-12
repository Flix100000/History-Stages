package net.bananemdnsa.historystages.platform.event;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.bananemdnsa.historystages.platform.bus.ICancellableEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The anvil working out what the two input items would make. Cancelling leaves the output slot
 * empty, which is how a locked enchantment stops being applicable without hiding the anvil.
 */
public class AnvilUpdateEvent extends Event implements ICancellableEvent {

    private final ItemStack left;
    private final ItemStack right;
    private final Player player;

    public AnvilUpdateEvent(ItemStack left, ItemStack right, Player player) {
        this.left = left;
        this.right = right;
        this.player = player;
    }

    public ItemStack getLeft() {
        return left;
    }

    public ItemStack getRight() {
        return right;
    }

    public Player getPlayer() {
        return player;
    }
}
