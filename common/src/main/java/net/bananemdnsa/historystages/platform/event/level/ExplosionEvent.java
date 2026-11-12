package net.bananemdnsa.historystages.platform.event.level;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Explosion;

import java.util.List;

public abstract class ExplosionEvent extends Event {

    private final Level level;
    private final Explosion explosion;

    protected ExplosionEvent(Level level, Explosion explosion) {
        this.level = level;
        this.explosion = explosion;
    }

    public Level getLevel() {
        return level;
    }

    public Explosion getExplosion() {
        return explosion;
    }

    /**
     * The blocks an explosion has worked out it will destroy, before it does.
     *
     * <p>The list is live and handlers take entries out of it, which is how a zone keeps its
     * blocks while the explosion still goes off everywhere else. Cancelling the whole explosion
     * would also swallow the damage and the sound.
     */
    public static class Detonate extends ExplosionEvent {

        private final List<BlockPos> affectedBlocks;

        public Detonate(Level level, Explosion explosion, List<BlockPos> affectedBlocks) {
            super(level, explosion);
            this.affectedBlocks = affectedBlocks;
        }

        public List<BlockPos> getAffectedBlocks() {
            return affectedBlocks;
        }
    }
}
