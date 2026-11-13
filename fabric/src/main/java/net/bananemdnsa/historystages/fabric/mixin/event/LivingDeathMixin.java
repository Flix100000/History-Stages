package net.bananemdnsa.historystages.fabric.mixin.event;

import net.bananemdnsa.historystages.fabric.events.Phases;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.living.LivingDeathEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Raises the death event at the start of {@code die}, where NeoForge raises it, and lets a cancel
 * stop the death. Not Fabric's ALLOW_DEATH: that one is asked before the totem, while NeoForge's
 * event (and StageDeathLossHandler, LOWEST "after revive mods") comes after it. A revive mod on
 * ALLOW_DEATH therefore still answers first. {@code ServerPlayer.die} does not call the
 * living-entity version, so both are hooked.
 */
@Mixin({LivingEntity.class, ServerPlayer.class})
public abstract class LivingDeathMixin {

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void historystages$raise(DamageSource source, CallbackInfo ci) {
        if (Phases.wanted(LivingDeathEvent.class)
                && EventBus.post(new LivingDeathEvent((LivingEntity) (Object) this, source)).isCanceled()) {
            ci.cancel();
        }
    }
}
