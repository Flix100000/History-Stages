package net.bananemdnsa.historystages.mixin.slotlock;

import net.bananemdnsa.historystages.util.lock.SlotLocks;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge hands vanilla containers and the player inventory to hoppers, pipes and mod "give"
 * helpers through this wrapper, never through HopperBlockEntity's own checks. So this is where a
 * real hopper, or a quest reward, meets a locked slot.
 */
@Mixin(value = InvWrapper.class, remap = false)
public abstract class InvWrapperMixin {

    @Shadow public abstract Container getInv();

    @Inject(method = "insertItem", at = @At("HEAD"), cancellable = true)
    private void historystages$insert(int slot, ItemStack stack, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        if (SlotLocks.handlerSlotBlocked(getInv(), slot)) cir.setReturnValue(stack);
    }

    @Inject(method = "extractItem", at = @At("HEAD"), cancellable = true)
    private void historystages$extract(int slot, int amount, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        if (SlotLocks.handlerSlotBlocked(getInv(), slot)) cir.setReturnValue(ItemStack.EMPTY);
    }
}
