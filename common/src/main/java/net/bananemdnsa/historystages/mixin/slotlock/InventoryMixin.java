package net.bananemdnsa.historystages.mixin.slotlock;

import net.bananemdnsa.historystages.util.lock.SlotLocks;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ground pickup, /give and "put it back" all find their slot through these two methods. Skipping
 * locked slots here means a full-but-for-locked-slots inventory leaves the item on the ground.
 */
@Mixin(Inventory.class)
public abstract class InventoryMixin {

    @Shadow @Final public NonNullList<ItemStack> items;
    @Shadow @Final public Player player;
    @Shadow public int selected;

    @Shadow public abstract ItemStack getItem(int index);
    @Shadow protected abstract boolean hasRemainingSpaceForItem(ItemStack destination, ItemStack origin);

    @Inject(method = "getFreeSlot", at = @At("HEAD"), cancellable = true)
    private void historystages$getFreeSlot(CallbackInfoReturnable<Integer> cir) {
        if (!SlotLocks.anyPlayerSlotLocks(player)) return;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).isEmpty() && !historystages$locked(i)) {
                cir.setReturnValue(i);
                return;
            }
        }
        cir.setReturnValue(-1);
    }

    @Inject(method = "getSlotWithRemainingSpace", at = @At("HEAD"), cancellable = true)
    private void historystages$getSlotWithRemainingSpace(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (!SlotLocks.anyPlayerSlotLocks(player)) return;
        // Same order as vanilla: held slot, offhand, then the rest.
        if (!historystages$locked(selected) && hasRemainingSpaceForItem(getItem(selected), stack)) {
            cir.setReturnValue(selected);
            return;
        }
        if (!historystages$locked(40) && hasRemainingSpaceForItem(getItem(40), stack)) {
            cir.setReturnValue(40);
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            if (!historystages$locked(i) && hasRemainingSpaceForItem(items.get(i), stack)) {
                cir.setReturnValue(i);
                return;
            }
        }
        cir.setReturnValue(-1);
    }

    /** Pick-block prefers an empty hotbar slot, and a swept locked slot is always empty. */
    @Inject(method = "getSuitableHotbarSlot", at = @At("HEAD"), cancellable = true)
    private void historystages$getSuitableHotbarSlot(CallbackInfoReturnable<Integer> cir) {
        if (!SlotLocks.anyPlayerSlotLocks(player)) return;
        for (int i = 0; i < 9; i++) {
            int slot = (selected + i) % 9;
            if (!historystages$locked(slot) && items.get(slot).isEmpty()) {
                cir.setReturnValue(slot);
                return;
            }
        }
        for (int i = 0; i < 9; i++) {
            int slot = (selected + i) % 9;
            if (!historystages$locked(slot) && !items.get(slot).isEnchanted()) {
                cir.setReturnValue(slot);
                return;
            }
        }
        cir.setReturnValue(selected);
    }

    /** Neither out of a locked slot, nor into a locked hotbar slot when no open one is left. */
    @Inject(method = "pickSlot", at = @At("HEAD"), cancellable = true)
    private void historystages$pickSlot(int index, CallbackInfo ci) {
        if (!SlotLocks.anyPlayerSlotLocks(player)) return;
        if (historystages$locked(index) || historystages$locked(((Inventory) (Object) this).getSuitableHotbarSlot())) {
            ci.cancel();
        }
    }

    private boolean historystages$locked(int index) {
        return SlotLocks.playerSlot(player, index).blocks();
    }
}
