package net.bananemdnsa.historystages.mixin.slotlock;

import net.bananemdnsa.historystages.util.lock.HotbarSelection;
import net.bananemdnsa.historystages.util.lock.SlotLocks;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scrolling the hotbar steps over locked slots instead of landing on them. */
@Mixin(Inventory.class)
public abstract class InventoryScrollMixin {

    @Shadow @Final public Player player;
    @Shadow public int selected;

    @Inject(method = "swapPaint", at = @At("HEAD"), cancellable = true)
    private void historystages$swapPaint(double direction, CallbackInfo ci) {
        if (!SlotLocks.anyPlayerSlotLocks(player)) return;
        // Vanilla subtracts: scrolling up moves to the left.
        int step = -(int) Math.signum(direction);
        if (step == 0) return;
        selected = HotbarSelection.next(selected, step, slot -> SlotLocks.playerSlot(player, slot).blocks());
        ci.cancel();
    }
}
