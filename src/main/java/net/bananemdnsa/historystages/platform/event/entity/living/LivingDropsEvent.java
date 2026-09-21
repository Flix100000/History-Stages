package net.bananemdnsa.historystages.platform.event.entity.living;

import net.bananemdnsa.historystages.platform.bus.ICancellableEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.Collection;

/**
 * What a mob is about to drop, while the list can still be changed. The collection is the live
 * one: a lock removes entries from it rather than answering with a new list.
 */
public class LivingDropsEvent extends LivingEvent implements ICancellableEvent {

    private final DamageSource source;
    private final Collection<ItemEntity> drops;

    public LivingDropsEvent(LivingEntity entity, DamageSource source, Collection<ItemEntity> drops) {
        super(entity);
        this.source = source;
        this.drops = drops;
    }

    public DamageSource getSource() {
        return source;
    }

    public Collection<ItemEntity> getDrops() {
        return drops;
    }
}
