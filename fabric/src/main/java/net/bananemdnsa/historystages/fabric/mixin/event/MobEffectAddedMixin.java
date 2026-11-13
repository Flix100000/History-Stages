package net.bananemdnsa.historystages.fabric.mixin.event;

import net.bananemdnsa.historystages.fabric.events.Phases;
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
 * Raises the effect-applied event, which an auto-trigger watches. Only when the effect took:
 * {@code addEffect} answers false for an immune entity or a weaker repeat. NeoForge's {@code Added}
 * covers exactly the successful {@code addEffect} calls, new effects and upgrades alike, so the
 * mixin from fabric-1.21.X stays rather than Fabric's {@code ServerMobEffectEvents}.
 */
@Mixin(LivingEntity.class)
public abstract class MobEffectAddedMixin {

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("RETURN"))
    private void historystages$raise(MobEffectInstance instance, Entity source, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue()) && Phases.wanted(MobEffectEvent.Added.class)) {
            EventBus.post(new MobEffectEvent.Added((LivingEntity) (Object) this, instance));
        }
    }
}
