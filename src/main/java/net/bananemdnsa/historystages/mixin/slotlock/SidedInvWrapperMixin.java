package net.bananemdnsa.historystages.mixin.slotlock;

import net.bananemdnsa.historystages.util.lock.SlotLocks;
import net.minecraft.core.Direction;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Furnaces, brewing stands and shulker boxes are sided; the wrapper slot is mapped to the real one first. */
@Mixin(value = SidedInvWrapper.class, remap = false)
public abstract class SidedInvWrapperMixin {

    @Shadow @Final protected WorldlyContainer inv;
    @Shadow @Final protected Direction side;

    @Inject(method = "insertItem", at = @At("HEAD"), cancellable = true)
    private void historystages$insert(int slot, ItemStack stack, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        if (historystages$blocked(slot)) cir.setReturnValue(stack);
    }

    @Inject(method = "extractItem", at = @At("HEAD"), cancellable = true)
    private void historystages$extract(int slot, int amount, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        if (historystages$blocked(slot)) cir.setReturnValue(ItemStack.EMPTY);
    }

    private boolean historystages$blocked(int wrapperSlot) {
        int real = SidedInvWrapper.getSlot(inv, wrapperSlot, side);
        return real >= 0 && SlotLocks.handlerSlotBlocked(inv, real);
    }
}
