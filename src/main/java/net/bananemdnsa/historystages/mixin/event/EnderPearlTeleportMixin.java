package net.bananemdnsa.historystages.mixin.event;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.EntityTeleportEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Raises the pearl-landing event, so a zone can refuse the spot a pearl would put someone in
 * without refusing teleports in general.
 *
 * <p>The thrower is what the event is about, not the pearl: the handler asks whether that player
 * may be where the pearl landed.
 */
@Mixin(ThrownEnderpearl.class)
public abstract class EnderPearlTeleportMixin {

    @Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
    private void historystages$raise(HitResult hitResult, CallbackInfo ci) {
        ThrownEnderpearl pearl = (ThrownEnderpearl) (Object) this;
        Entity thrower = pearl.getOwner();
        if (thrower == null) {
            return;
        }
        EntityTeleportEvent.EnderPearl event = EventBus.post(new EntityTeleportEvent.EnderPearl(
                thrower, hitResult.getLocation().x, hitResult.getLocation().y, hitResult.getLocation().z));
        if (event.isCanceled()) {
            ci.cancel();
        }
    }
}
