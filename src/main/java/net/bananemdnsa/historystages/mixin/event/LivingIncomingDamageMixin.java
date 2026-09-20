package net.bananemdnsa.historystages.mixin.event;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.living.LivingIncomingDamageEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises the incoming-damage event. Cancelling means none of the damage lands.
 *
 * <p>Fabric does have a damage callback, but it answers before armour and effects and is shaped
 * differently; the handler here only asks who is hitting whom, so the plain entry point is the
 * closer match to what the other loader raises.
 */
@Mixin(LivingEntity.class)
public abstract class LivingIncomingDamageMixin {

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void historystages$raise(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingIncomingDamageEvent event = EventBus.post(
                new LivingIncomingDamageEvent((LivingEntity) (Object) this, source));
        if (event.isCanceled()) {
            cir.setReturnValue(false);
        }
    }
}
