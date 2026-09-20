package net.bananemdnsa.historystages.platform.event.entity;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.bananemdnsa.historystages.platform.bus.ICancellableEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** An entity about to change dimension. Cancelling leaves it where it is. */
public class EntityTravelToDimensionEvent extends Event implements ICancellableEvent {

    private final Entity entity;
    private final ResourceKey<Level> dimension;

    public EntityTravelToDimensionEvent(Entity entity, ResourceKey<Level> dimension) {
        this.entity = entity;
        this.dimension = dimension;
    }

    public Entity getEntity() {
        return entity;
    }

    public ResourceKey<Level> getDimension() {
        return dimension;
    }
}
