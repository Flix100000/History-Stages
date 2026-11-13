package net.bananemdnsa.historystages.fabric.mixin.event;

import net.bananemdnsa.historystages.fabric.events.Phases;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.ProjectileImpactEvent;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Raises the projectile-hit event. Cancelling lets the projectile carry on as though it had
 * missed, which is how a zone stops arrows at its wall without deleting them mid-air.
 */
@Mixin(Projectile.class)
public abstract class ProjectileImpactMixin {

    @Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
    private void historystages$raise(HitResult hitResult, CallbackInfo ci) {
        if (Phases.wanted(ProjectileImpactEvent.class)
                && EventBus.post(new ProjectileImpactEvent((Projectile) (Object) this, hitResult)).isCanceled()) {
            ci.cancel();
        }
    }
}
