package net.bananemdnsa.historystages.platform.event.entity.living;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.minecraft.world.entity.LivingEntity;

/** Shared base for the events that are about one living entity. */
public abstract class LivingEvent extends Event {

    private final LivingEntity entity;

    protected LivingEvent(LivingEntity entity) {
        this.entity = entity;
    }

    public LivingEntity getEntity() {
        return entity;
    }
}
