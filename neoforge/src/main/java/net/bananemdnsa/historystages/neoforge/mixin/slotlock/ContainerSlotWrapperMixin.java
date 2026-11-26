package net.bananemdnsa.historystages.neoforge.mixin.slotlock;

import net.bananemdnsa.historystages.util.lock.SlotLocks;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge 26.2 hands vanilla containers and the player inventory to pipes, machines and mod
 * "give" helpers through one wrapper per slot; this is what InvWrapper was on 1.21.1. The sided
 * wrapper (furnaces, brewing stands, shulker boxes) maps the face and then calls this one with the
 * real slot, so it is covered too. NeoForge's hopper hooks come through here as well.
 */
@Mixin(targets = "net.neoforged.neoforge.transfer.item.VanillaContainerWrapper$SlotWrapper")
public abstract class ContainerSlotWrapperMixin {

    @Shadow @Final private int index;

    @Shadow @Final VanillaContainerWrapper this$0;

    @Inject(method = "insert(ILnet/neoforged/neoforge/transfer/item/ItemResource;ILnet/neoforged/neoforge/transfer/transaction/TransactionContext;)I",
            at = @At("HEAD"), cancellable = true)
    private void historystages$insert(int slot, ItemResource resource, int amount, TransactionContext transaction,
                                      CallbackInfoReturnable<Integer> cir) {
        if (historystages$blocked()) cir.setReturnValue(0);
    }

    @Inject(method = "extract(ILnet/neoforged/neoforge/transfer/item/ItemResource;ILnet/neoforged/neoforge/transfer/transaction/TransactionContext;)I",
            at = @At("HEAD"), cancellable = true)
    private void historystages$extract(int slot, ItemResource resource, int amount, TransactionContext transaction,
                                       CallbackInfoReturnable<Integer> cir) {
        if (historystages$blocked()) cir.setReturnValue(0);
    }

    private boolean historystages$blocked() {
        return SlotLocks.handlerSlotBlocked(((VanillaContainerWrapperAccessor) this$0).historystages$container(), index);
    }
}
