package net.bananemdnsa.historystages.mixin.event;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.living.MobEffectEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises the effect-applied event, which an auto-trigger can watch for.
 *
 * <p>At RETURN and only when the effect actually took: addEffect answers false for an effect the
 * entity is immune to or already has at a higher level, and a trigger should not fire on those.
 */
@Mixin(LivingEntity.class)
public abstract class MobEffectAddedMixin {

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("RETURN"))
    private void historystages$raise(MobEffectInstance instance, Entity source,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            EventBus.post(new MobEffectEvent.Added((LivingEntity) (Object) this, instance));
        }
    }
}
