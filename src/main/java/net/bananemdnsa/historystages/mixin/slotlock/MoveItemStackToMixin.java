package net.bananemdnsa.historystages.mixin.slotlock;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bananemdnsa.historystages.util.lock.SlotLocks;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Shift-click first tops up matching stacks without asking mayPlace. A locked slot looks empty to
 * that first pass, so its stack is never added to. Only the first pass: the second one places into
 * empty slots and does ask mayPlace, and lying to it could overwrite a mod slot's item.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class MoveItemStackToMixin {

    @WrapOperation(method = "moveItemStackTo",
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack historystages$skipLockedMerge(Slot slot, Operation<ItemStack> original) {
        if (SlotLocks.stateOf(slot, null).blocks()) return ItemStack.EMPTY;
        return original.call(slot);
    }
}
