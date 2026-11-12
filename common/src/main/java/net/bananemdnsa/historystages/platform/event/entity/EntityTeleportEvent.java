package net.bananemdnsa.historystages.platform.event.entity;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.bananemdnsa.historystages.platform.bus.ICancellableEvent;
import net.minecraft.world.entity.Entity;

/** An entity being moved somewhere other than by walking. */
public class EntityTeleportEvent extends Event implements ICancellableEvent {

    private final Entity entity;
    private double targetX;
    private double targetY;
    private double targetZ;

    public EntityTeleportEvent(Entity entity, double targetX, double targetY, double targetZ) {
        this.entity = entity;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
    }

    public Entity getEntity() {
        return entity;
    }

    public double getTargetX() {
        return targetX;
    }

    public double getTargetY() {
        return targetY;
    }

    public double getTargetZ() {
        return targetZ;
    }

    public void setTargetX(double x) {
        this.targetX = x;
    }

    public void setTargetY(double y) {
        this.targetY = y;
    }

    public void setTargetZ(double z) {
        this.targetZ = z;
    }

    /**
     * A pearl landing. It is its own event because a zone can refuse the landing spot without
     * refusing teleports in general.
     */
    public static class EnderPearl extends EntityTeleportEvent {
        public EnderPearl(Entity entity, double targetX, double targetY, double targetZ) {
            super(entity, targetX, targetY, targetZ);
        }
    }
}
