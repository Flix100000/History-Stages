package net.bananemdnsa.historystages.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ResultContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches the three fields an anvil keeps on its superclass.
 *
 * <p>A shadow only finds what the target class declares, and the anvil declares none of these —
 * {@code ItemCombinerMenu} does. Shadowing them from a mixin on the anvil looks right and fails
 * when the game starts, which is exactly how it failed once.
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
