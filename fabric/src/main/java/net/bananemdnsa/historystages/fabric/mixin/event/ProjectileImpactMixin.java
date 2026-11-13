package net.bananemdnsa.historystages.fabric.mixin.event;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bananemdnsa.historystages.fabric.events.Phases;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.ProjectileImpactEvent;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Raises the projectile-hit event before {@code onHit} is called, as NeoForge does. Cancelling
 * skips the whole hit, so the projectile carries on as though it had missed; that is how a zone
 * stops arrows at its wall.
 *
 * <p>At the call site in {@code hitTargetOrDeflectSelf}, not at the head of {@code onHit}: the
 * subclasses override {@code onHit}, call super and then do their own work (a splash potion
 * splashes, a fireball explodes, an egg hatches chickens), so a cancel at the head of the base
 * method let all of that through.
 */
@Mixin(Projectile.class)
public abstract class ProjectileImpactMixin {

    @WrapOperation(method = "hitTargetOrDeflectSelf", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/Projectile;onHit(Lnet/minecraft/world/phys/HitResult;)V"))
    private void historystages$raise(Projectile self, HitResult hitResult, Operation<Void> original) {
        if (Phases.wanted(ProjectileImpactEvent.class)
                && EventBus.post(new ProjectileImpactEvent(self, hitResult)).isCanceled()) {
            return;
        }
        original.call(self, hitResult);
    }
}
