package net.bananemdnsa.historystages.platform.event.level;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A plant about to grow in a random tick.
 *
 * <p>NeoForge asks this from each growing block it patches. Fabric has no such hook, so it is
 * raised once for every random tick that goes through {@code BlockStateBase.randomTick}; see
 * {@code mixin/event/PlantRandomTickMixin}.
 */
public abstract class CropGrowEvent extends Event {

    private final LevelAccessor level;
    private final BlockPos pos;
    private final BlockState state;

    protected CropGrowEvent(LevelAccessor level, BlockPos pos, BlockState state) {
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

    /** Before the growth. {@link Result#DO_NOT_GROW} skips the tick. */
    public static class Pre extends CropGrowEvent {

        public enum Result { DEFAULT, GROW, DO_NOT_GROW }

        private Result result = Result.DEFAULT;

        public Pre(LevelAccessor level, BlockPos pos, BlockState state) {
            super(level, pos, state);
        }

        public Result getResult() {
            return result;
        }

        public void setResult(Result result) {
            this.result = result;
        }
    }
}
