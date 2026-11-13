package net.bananemdnsa.historystages.fabric.mixin.event;

import net.bananemdnsa.historystages.fabric.events.Phases;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.EntityTeleportEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Raises the pearl-landing event, so a zone can refuse the spot a pearl would put someone in. The
 * event is about the thrower, not the pearl. Only the pearl: the plain {@code EntityTeleportEvent}
 * (commands, chorus fruit) has no source on Fabric, and nothing on main listens for it.
 */
@Mixin(ThrownEnderpearl.class)
public abstract class EnderPearlTeleportMixin {

    @Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
    private void historystages$raise(HitResult hitResult, CallbackInfo ci) {
        Entity thrower = ((ThrownEnderpearl) (Object) this).getOwner();
        if (thrower == null || !Phases.wanted(EntityTeleportEvent.EnderPearl.class)) {
            return;
        }
        if (EventBus.post(new EntityTeleportEvent.EnderPearl(thrower, hitResult.getLocation().x,
                hitResult.getLocation().y, hitResult.getLocation().z)).isCanceled()) {
            ci.cancel();
        }
    }
}
