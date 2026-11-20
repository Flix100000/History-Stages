package net.bananemdnsa.historystages.mixin.slotlock;

import net.bananemdnsa.historystages.util.lock.SlotLocks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Most mod machines use this slot, and it answers mayPlace/mayPickup without asking Slot. */
@Mixin(value = SlotItemHandler.class, remap = false)
public abstract class SlotItemHandlerMixin {

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true, remap = true)
    private void historystages$mayPlace(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (SlotLocks.stateOf((Slot) (Object) this, null).blocks()) cir.setReturnValue(false);
    }

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true, remap = true)
    private void historystages$mayPickup(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (SlotLocks.stateOf((Slot) (Object) this, player).blocks()) cir.setReturnValue(false);
    }
}
