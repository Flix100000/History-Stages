package net.bananemdnsa.historystages.platform.event.entity.player;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** The lines under an item in a tooltip, while they can still be added to. */
public class ItemTooltipEvent extends Event {

    private final ItemStack stack;
    private final java.util.List<Component> tooltip;

    public ItemTooltipEvent(ItemStack stack, java.util.List<Component> tooltip) {
        this.stack = stack;
        this.tooltip = tooltip;
    }

    public ItemStack getItemStack() {
        return stack;
    }

    /** The live list, so handlers add to it in place as they do on the other loader. */
    public java.util.List<Component> getToolTip() {
        return tooltip;
    }
}
