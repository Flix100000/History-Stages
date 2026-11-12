package net.bananemdnsa.historystages.platform.event.entity.living;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.bananemdnsa.historystages.platform.bus.ICancellableEvent;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Mob;

/**
 * Two mobs breeding. Cancelling means no baby, which is how a spawn lock covers breeding.
 *
 * <p>The parents are typed as plain {@link Mob}s because NeoForge 26.2 hands them over that way.
 * The child can be null when another mod already took it away.
 */
public class BabyEntitySpawnEvent extends Event implements ICancellableEvent {

    private final Mob parentA;
    private final Mob parentB;
    private AgeableMob child;

    public BabyEntitySpawnEvent(Mob parentA, Mob parentB, AgeableMob child) {
        this.parentA = parentA;
        this.parentB = parentB;
        this.child = child;
    }

    public Mob getParentA() {
        return parentA;
    }

    public Mob getParentB() {
        return parentB;
    }

    public AgeableMob getChild() {
        return child;
    }

    public void setChild(AgeableMob child) {
        this.child = child;
    }
}
