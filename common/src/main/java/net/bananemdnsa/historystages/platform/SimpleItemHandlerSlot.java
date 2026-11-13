package net.bananemdnsa.historystages.platform;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Stands in for NeoForge's {@code SlotItemHandler}: a menu slot that asks the handler, so placing
 * and taking follow {@link SimpleItemHandler#isItemValid} and {@link SimpleItemHandler#extractItem}
 * (the pedestal's scroll lock) instead of plain container access.
 */
public class SimpleItemHandlerSlot extends Slot {

    private final SimpleItemHandler handler;
    private final int index;

    public SimpleItemHandlerSlot(SimpleItemHandler handler, int index, int x, int y) {
        super(handler, index, x, y);
        this.handler = handler;
        this.index = index;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return !stack.isEmpty() && handler.isItemValid(index, stack);
    }

    @Override
    public boolean mayPickup(Player player) {
        return !handler.extractItem(index, 1, true).isEmpty();
    }

    @Override
    public ItemStack remove(int amount) {
        return handler.extractItem(index, amount, false);
    }
}
