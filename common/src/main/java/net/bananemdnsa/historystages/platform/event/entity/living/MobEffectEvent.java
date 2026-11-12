package net.bananemdnsa.historystages.platform.event.entity.living;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

public abstract class MobEffectEvent extends LivingEvent {

    private final MobEffectInstance effectInstance;

    protected MobEffectEvent(LivingEntity entity, MobEffectInstance effectInstance) {
        super(entity);
        this.effectInstance = effectInstance;
    }

    public MobEffectInstance getEffectInstance() {
        return effectInstance;
    }

    /** An effect that has just been applied, which an auto-trigger can watch for. */
    public static class Added extends MobEffectEvent {
        public Added(LivingEntity entity, MobEffectInstance effectInstance) {
            super(entity, effectInstance);
        }
    }
}
