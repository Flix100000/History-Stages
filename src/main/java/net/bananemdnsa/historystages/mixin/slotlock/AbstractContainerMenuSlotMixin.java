package net.bananemdnsa.historystages.mixin.slotlock;

import net.bananemdnsa.historystages.util.lock.SlotLockMenu;
import net.bananemdnsa.historystages.util.lock.SlotMenuOwner;
import net.bananemdnsa.historystages.util.lock.SlotTargets;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuSlotMixin implements SlotLockMenu {

    @Unique private String historystages$target;
    @Unique private boolean historystages$targetResolved;

    @Inject(method = "addSlot", at = @At("TAIL"))
    private void historystages$rememberOwner(Slot slot, CallbackInfoReturnable<Slot> cir) {
        ((SlotMenuOwner) slot).historystages$setMenu((AbstractContainerMenu) (Object) this);
    }

    @Override
    public String historystages$slotTarget() {
        // Resolved lazily: slots are still being added while the constructor runs.
        if (!historystages$targetResolved) {
            historystages$target = SlotTargets.resolve((AbstractContainerMenu) (Object) this);
            historystages$targetResolved = true;
        }
        return historystages$target;
    }

    @Override
    public void historystages$setSlotTarget(String target) {
        historystages$target = target;
        historystages$targetResolved = true;
    }
}
