package net.bananemdnsa.historystages.platform.event.level;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.bananemdnsa.historystages.platform.bus.ICancellableEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

public abstract class BlockEvent extends Event {

    private final LevelAccessor level;
    private final BlockPos pos;
    private final BlockState state;

    protected BlockEvent(LevelAccessor level, BlockPos pos, BlockState state) {
        this.level = level;
        this.pos = pos;
        this.state = state;
    }

    public LevelAccessor getLevel() {
        return level;
    }

    public BlockPos getPos() {
        return pos;
    }

    public BlockState getState() {
        return state;
    }

    /** A block about to be broken by a player. Cancelling leaves it standing. */
    public static class BreakEvent extends BlockEvent implements ICancellableEvent {

        private final Player player;

        public BreakEvent(LevelAccessor level, BlockPos pos, BlockState state, Player player) {
            super(level, pos, state);
            this.player = player;
        }

        public Player getPlayer() {
            return player;
        }
    }

    /** A block that has just been placed by an entity. */
    public static class EntityPlaceEvent extends BlockEvent implements ICancellableEvent {

        private final Entity entity;

        public EntityPlaceEvent(LevelAccessor level, BlockPos pos, BlockState placed, Entity entity) {
            super(level, pos, placed);
            this.entity = entity;
        }

        public Entity getEntity() {
            return entity;
        }

        public BlockState getPlacedBlock() {
            return getState();
        }
    }
}
