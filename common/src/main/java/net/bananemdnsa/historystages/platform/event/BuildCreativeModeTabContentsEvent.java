package net.bananemdnsa.historystages.platform.event;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * A creative tab being filled, which is how this mod puts the pedestal into the vanilla tabs and
 * a scroll for every stage into the ingredients tab.
 *
 * <p>It is fired once per tab rather than once overall, so a handler asks which tab it is looking
 * at before adding anything.
 */
public class BuildCreativeModeTabContentsEvent extends Event {

    private final ResourceKey<CreativeModeTab> tabKey;
    private final Consumer<ItemStack> output;

    public BuildCreativeModeTabContentsEvent(ResourceKey<CreativeModeTab> tabKey, Consumer<ItemStack> output) {
        this.tabKey = tabKey;
        this.output = output;
    }

    public ResourceKey<CreativeModeTab> getTabKey() {
        return tabKey;
    }

    public void accept(ItemStack stack) {
        output.accept(stack);
    }

    public void accept(ItemLike item) {
        output.accept(new ItemStack(item));
    }
}
