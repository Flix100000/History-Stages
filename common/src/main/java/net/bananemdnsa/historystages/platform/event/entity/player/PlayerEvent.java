package net.bananemdnsa.historystages.platform.event.entity.player;

import net.bananemdnsa.historystages.platform.bus.Event;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Things that happen to a player rather than because of one. */
public abstract class PlayerEvent extends Event {

    private final Player player;

    protected PlayerEvent(Player player) {
        this.player = player;
    }

    public Player getEntity() {
        return player;
    }

    /**
     * How fast the player is breaking a block. Setting the speed to zero is how a lock stops a
     * block being broken without cancelling anything, which leaves the swing animation intact.
     */
    public static class BreakSpeed extends PlayerEvent {

        private final BlockState state;
        private final float originalSpeed;
        private final @Nullable BlockPos pos;
        private float newSpeed;

        public BreakSpeed(Player player, BlockState state, float originalSpeed) {
            this(player, state, originalSpeed, null);
        }

        public BreakSpeed(Player player, BlockState state, float originalSpeed, @Nullable BlockPos pos) {
            super(player);
            this.state = state;
            this.originalSpeed = originalSpeed;
            this.pos = pos;
            this.newSpeed = originalSpeed;
        }

        /** Empty when the speed is asked for without a block in the world (NeoForge's contract too). */
        public Optional<BlockPos> getPosition() {
            return Optional.ofNullable(pos);
        }

        public BlockState getState() {
            return state;
        }

        public float getOriginalSpeed() {
            return originalSpeed;
        }

        public float getNewSpeed() {
            return newSpeed;
        }

        public void setNewSpeed(float newSpeed) {
            this.newSpeed = newSpeed;
        }
    }

    /**
     * Whether the player's tool gets drops out of the block (NeoForge's {@code HarvestCheck}).
     * Level and position are null where the question is asked without them.
     */
    public static class HarvestCheck extends PlayerEvent {

        private final BlockState state;
        private final @Nullable BlockGetter level;
        private final @Nullable BlockPos pos;
        private boolean canHarvest;

        public HarvestCheck(Player player, BlockState state, @Nullable BlockGetter level, @Nullable BlockPos pos,
                            boolean canHarvest) {
            super(player);
            this.state = state;
            this.level = level;
            this.pos = pos;
            this.canHarvest = canHarvest;
        }

        public BlockState getTargetBlock() {
            return state;
        }

        public @Nullable BlockGetter getLevel() {
            return level;
        }

        public @Nullable BlockPos getPos() {
            return pos;
        }

        public boolean canHarvest() {
            return canHarvest;
        }

        public void setCanHarvest(boolean canHarvest) {
            this.canHarvest = canHarvest;
        }
    }

    public static class PlayerLoggedInEvent extends PlayerEvent {
        public PlayerLoggedInEvent(Player player) {
            super(player);
        }
    }

    public static class PlayerLoggedOutEvent extends PlayerEvent {
        public PlayerLoggedOutEvent(Player player) {
            super(player);
        }
    }

    public static class PlayerChangedDimensionEvent extends PlayerEvent {

        private final ResourceKey<Level> from;
        private final ResourceKey<Level> to;

        public PlayerChangedDimensionEvent(Player player, ResourceKey<Level> from, ResourceKey<Level> to) {
            super(player);
            this.from = from;
            this.to = to;
        }

        public ResourceKey<Level> getFrom() {
            return from;
        }

        public ResourceKey<Level> getTo() {
            return to;
        }
    }
}
