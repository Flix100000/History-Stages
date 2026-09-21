package net.bananemdnsa.historystages.platform.event.entity.living;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.bananemdnsa.historystages.platform.bus.ICancellableEvent;
import net.minecraft.world.entity.AgeableMob;

/** Two mobs breeding. Cancelling means no baby, which is how a spawn lock covers breeding. */
public class BabyEntitySpawnEvent extends Event implements ICancellableEvent {

    private final AgeableMob parentA;
    private final AgeableMob parentB;
    private AgeableMob child;

    public BabyEntitySpawnEvent(AgeableMob parentA, AgeableMob parentB, AgeableMob child) {
        this.parentA = parentA;
        this.parentB = parentB;
        this.child = child;
    }

    public AgeableMob getParentA() {
        return parentA;
    }

    public AgeableMob getParentB() {
        return parentB;
    }

    public AgeableMob getChild() {
        return child;
    }

    public void setChild(AgeableMob child) {
        this.child = child;
    }
}
