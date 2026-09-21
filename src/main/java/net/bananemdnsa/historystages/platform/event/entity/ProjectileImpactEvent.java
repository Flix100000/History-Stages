package net.bananemdnsa.historystages.platform.event.entity;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.bananemdnsa.historystages.platform.bus.ICancellableEvent;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;

/** A projectile hitting something. Cancelling makes it carry on as if it had missed. */
public class ProjectileImpactEvent extends Event implements ICancellableEvent {

    private final Projectile projectile;
    private final HitResult rayTraceResult;

    public ProjectileImpactEvent(Projectile projectile, HitResult rayTraceResult) {
        this.projectile = projectile;
        this.rayTraceResult = rayTraceResult;
    }

    public Projectile getProjectile() {
        return projectile;
    }

    public HitResult getRayTraceResult() {
        return rayTraceResult;
    }
}
