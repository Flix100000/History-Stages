package net.bananemdnsa.historystages.platform.event.entity;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.bananemdnsa.historystages.platform.bus.ICancellableEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** An entity being added to a level. Cancelling keeps it out. */
public class EntityJoinLevelEvent extends Event implements ICancellableEvent {

    private final Entity entity;
    private final Level level;

    public EntityJoinLevelEvent(Entity entity, Level level) {
        this.entity = entity;
        this.level = level;
    }

    public Entity getEntity() {
        return entity;
    }

    public Level getLevel() {
        return level;
    }
}
