package net.bananemdnsa.historystages.mixin.slotlock;

import net.bananemdnsa.historystages.util.lock.SlotLocks;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hoppers, droppers and hopper minecarts move items through these two checks. A globally locked
 * slot of a vanilla container is closed to them too; mod containers are left to their own handlers.
 */
@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {

    @Inject(method = "canPlaceItemInContainer", at = @At("HEAD"), cancellable = true)
    private static void historystages$canPlace(Container container, ItemStack stack, int slot, Direction direction,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (SlotLocks.automationBlocked(container, slot)) cir.setReturnValue(false);
    }

    /** {@code destination} is the container being taken from — vanilla's naming, not ours. */
    @Inject(method = "canTakeItemFromContainer", at = @At("HEAD"), cancellable = true)
    private static void historystages$canTake(Container source, Container destination, ItemStack stack, int slot,
                                              Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (SlotLocks.automationBlocked(destination, slot)) cir.setReturnValue(false);
    }
}
