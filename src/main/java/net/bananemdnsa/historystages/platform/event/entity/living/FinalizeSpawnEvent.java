package net.bananemdnsa.historystages.platform.event.entity.living;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.bananemdnsa.historystages.platform.bus.ICancellableEvent;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * The last word before a mob is finished and placed.
 *
 * <p>There are two ways to stop it and they are not the same. Cancelling only skips the
 * finalising step, and the mob still arrives, plain. {@link #setSpawnCancelled(boolean)} is what
 * keeps it out of the world altogether, and that is what a lock wants.
 */
public class FinalizeSpawnEvent extends Event implements ICancellableEvent {

    private final Mob mob;
    private final ServerLevelAccessor level;
    private final double x;
    private final double y;
    private final double z;
    private final MobSpawnType spawnType;
    private boolean spawnCancelled;

    public FinalizeSpawnEvent(Mob mob, ServerLevelAccessor level, double x, double y, double z,
                              MobSpawnType spawnType) {
        this.mob = mob;
        this.level = level;
        this.x = x;
        this.y = y;
        this.z = z;
        this.spawnType = spawnType;
    }

    public Mob getEntity() {
        return mob;
    }

    public ServerLevelAccessor getLevel() {
        return level;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public MobSpawnType getSpawnType() {
        return spawnType;
    }

    public boolean isSpawnCancelled() {
        return spawnCancelled;
    }

    public void setSpawnCancelled(boolean spawnCancelled) {
        this.spawnCancelled = spawnCancelled;
    }
}
