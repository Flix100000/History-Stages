package net.bananemdnsa.historystages.platform.event.entity.living;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * The two questions natural spawning asks before a mob exists.
 *
 * <p>Both answer with three states rather than a cancel: FAIL refuses this spot, SUCCEED forces it
 * past vanilla's own check, and DEFAULT leaves the decision alone. A plain cancel could only ever
 * mean FAIL, and a zone that wants to allow a spawn vanilla would refuse would have no way to say
 * so.
 */
public abstract class MobSpawnEvent extends Event {

    private final ServerLevelAccessor level;
    private final double x;
    private final double y;
    private final double z;
    private final MobSpawnType spawnType;

    protected MobSpawnEvent(ServerLevelAccessor level, double x, double y, double z, MobSpawnType spawnType) {
        this.level = level;
        this.x = x;
        this.y = y;
        this.z = z;
        this.spawnType = spawnType;
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

    /** Whether this particular mob may stand here. */
    public static class PositionCheck extends MobSpawnEvent {

        public enum Result {
            SUCCEED,
            DEFAULT,
            FAIL
        }

        private final Mob mob;
        private Result result = Result.DEFAULT;

        public PositionCheck(Mob mob, ServerLevelAccessor level, MobSpawnType spawnType,
                             double x, double y, double z) {
            super(level, x, y, z, spawnType);
            this.mob = mob;
        }

        public Mob getEntity() {
            return mob;
        }

        public Result getResult() {
            return result;
        }

        public void setResult(Result result) {
            this.result = result;
        }
    }

    /** Whether this kind of mob may spawn here at all, asked before one is created. */
    public static class SpawnPlacementCheck extends MobSpawnEvent {

        public enum Result {
            SUCCEED,
            DEFAULT,
            FAIL
        }

        private final EntityType<?> entityType;
        private final BlockPos pos;
        private Result result = Result.DEFAULT;

        public SpawnPlacementCheck(EntityType<?> entityType, ServerLevelAccessor level,
                                   MobSpawnType spawnType, BlockPos pos) {
            super(level, pos.getX(), pos.getY(), pos.getZ(), spawnType);
            this.entityType = entityType;
            this.pos = pos;
        }

        public EntityType<?> getEntityType() {
            return entityType;
        }

        public BlockPos getPos() {
            return pos;
        }

        public Result getResult() {
            return result;
        }

        public void setResult(Result result) {
            this.result = result;
        }
    }
}
