package net.bananemdnsa.historystages.fabric.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ResultContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches the three fields an anvil keeps on its superclass. A {@code @Shadow} on the anvil mixin
 * only finds fields the anvil itself declares: it compiles, then fails at startup (614c4fbe on
 * fabric-1.21.X). Must stay listed in the mixin config, or the first cast to it throws
 * IllegalClassLoadError when a player opens an anvil (Issue #103).
 */
@Mixin(ItemCombinerMenu.class)
public interface ItemCombinerMenuAccessor {
    @Accessor("player")
    Player historystages$getPlayer();

    @Accessor("inputSlots")
    Container historystages$getInputSlots();

    @Accessor("resultSlots")
    ResultContainer historystages$getResultSlots();
}
