package net.bananemdnsa.historystages.mixin.slotlock;

import net.bananemdnsa.historystages.util.lock.SlotLockState;
import net.bananemdnsa.historystages.util.lock.SlotLocks;
import net.bananemdnsa.historystages.util.lock.SlotMenuOwner;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Locked slots refuse items in both directions, and hidden ones stop existing for rendering and
 * hovering. Every vanilla menu path (click, shift-click, drag, collect) asks these three.
 */
@Mixin(Slot.class)
public abstract class SlotMixin implements SlotMenuOwner {

    @Unique private AbstractContainerMenu historystages$menu;

    @Override
    public AbstractContainerMenu historystages$menu() {
        return historystages$menu;
    }

    @Override
    public void historystages$setMenu(AbstractContainerMenu menu) {
        historystages$menu = menu;
    }

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void historystages$mayPlace(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (SlotLocks.stateOf((Slot) (Object) this, null).blocks()) cir.setReturnValue(false);
    }

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void historystages$mayPickup(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (SlotLocks.stateOf((Slot) (Object) this, player).blocks()) cir.setReturnValue(false);
    }

    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void historystages$isActive(CallbackInfoReturnable<Boolean> cir) {
        if (SlotLocks.stateOf((Slot) (Object) this, null) == SlotLockState.HIDDEN) cir.setReturnValue(false);
    }
}
