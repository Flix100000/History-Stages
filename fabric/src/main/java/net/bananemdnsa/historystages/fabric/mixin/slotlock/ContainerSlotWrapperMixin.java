package net.bananemdnsa.historystages.fabric.mixin.slotlock;

import net.bananemdnsa.historystages.util.lock.SlotLocks;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.Container;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric's transfer API hands vanilla containers and the player inventory to pipes, machines and
 * mod "give" helpers through one wrapper per slot. It is what NeoForge's InvWrapper was on 1.21.1,
 * so this is where a mod insert, or a quest reward, meets a locked slot. The sided wrapper calls
 * this one with the real slot, so furnaces and shulker boxes are covered too.
 */
@Mixin(targets = "net.fabricmc.fabric.impl.transfer.item.ContainerSlotWrapper")
public abstract class ContainerSlotWrapperMixin {

    @Shadow @Final int slot;

    @Unique
    private Container historystages$container;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void historystages$remember(@Coerce Object storage, int slot, CallbackInfo ci) {
        historystages$container = ((ContainerStorageImplAccessor) storage).historystages$container();
    }

    @Inject(method = "insert(Lnet/fabricmc/fabric/api/transfer/v1/item/ItemVariant;JLnet/fabricmc/fabric/api/transfer/v1/transaction/TransactionContext;)J",
            at = @At("HEAD"), cancellable = true)
    private void historystages$insert(ItemVariant resource, long maxAmount, TransactionContext transaction,
                                      CallbackInfoReturnable<Long> cir) {
        if (SlotLocks.handlerSlotBlocked(historystages$container, slot)) cir.setReturnValue(0L);
    }

    @Inject(method = "extract(Lnet/fabricmc/fabric/api/transfer/v1/item/ItemVariant;JLnet/fabricmc/fabric/api/transfer/v1/transaction/TransactionContext;)J",
            at = @At("HEAD"), cancellable = true)
    private void historystages$extract(ItemVariant resource, long maxAmount, TransactionContext transaction,
                                       CallbackInfoReturnable<Long> cir) {
        if (SlotLocks.handlerSlotBlocked(historystages$container, slot)) cir.setReturnValue(0L);
    }
}
