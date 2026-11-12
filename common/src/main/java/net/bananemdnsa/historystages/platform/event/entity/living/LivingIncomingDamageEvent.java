package net.bananemdnsa.historystages.platform.event.entity.living;

import net.bananemdnsa.historystages.platform.bus.ICancellableEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Damage on its way to a living entity. Cancelling means none of it lands. */
public class LivingIncomingDamageEvent extends LivingEvent implements ICancellableEvent {

    private final DamageSource source;

    public LivingIncomingDamageEvent(LivingEntity entity, DamageSource source) {
        super(entity);
        this.source = source;
    }

    public DamageSource getSource() {
        return source;
    }
}
