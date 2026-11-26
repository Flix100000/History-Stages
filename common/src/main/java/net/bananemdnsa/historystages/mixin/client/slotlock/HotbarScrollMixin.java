package net.bananemdnsa.historystages.mixin.client.slotlock;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bananemdnsa.historystages.util.lock.HotbarSelection;
import net.bananemdnsa.historystages.util.lock.SlotLocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Scrolling the hotbar steps over locked slots instead of landing on them. 1.21.1 did the step in
 * {@code Inventory.swapPaint}; since 26.x the mouse handler works out the next slot itself.
 */
@Mixin(MouseHandler.class)
public abstract class HotbarScrollMixin {

    @WrapOperation(method = "onScroll", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/ScrollWheelHandler;getNextScrollWheelSelection(DII)I"))
    private int historystages$skipLocked(double direction, int current, int max, Operation<Integer> original) {
        Player player = Minecraft.getInstance().player;
        if (player == null || !SlotLocks.anyPlayerSlotLocks(player)) return original.call(direction, current, max);
        // Vanilla subtracts: scrolling up moves to the left.
        int step = -(int) Math.signum(direction);
        if (step == 0) return original.call(direction, current, max);
        return HotbarSelection.next(current, step, slot -> SlotLocks.playerSlot(player, slot).blocks());
    }
}
