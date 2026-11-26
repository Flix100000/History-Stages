package net.bananemdnsa.historystages.platform.event.entity.living;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.bananemdnsa.historystages.platform.bus.ICancellableEvent;
import net.minecraft.world.entity.LivingEntity;

/** A living entity swapping items; see {@code mixin/event/SwapHandsMixin}. */
public abstract class LivingSwapItemsEvent extends Event {

    private final LivingEntity entity;

    protected LivingSwapItemsEvent(LivingEntity entity) {
        this.entity = entity;
    }

    public LivingEntity getEntity() {
        return entity;
    }

    /** Main hand and offhand about to trade places (the F key). Cancelling keeps both. */
    public static class Hands extends LivingSwapItemsEvent implements ICancellableEvent {
        public Hands(LivingEntity entity) {
            super(entity);
        }
    }
}
