package net.bananemdnsa.historystages.platform.event.entity.player;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.bananemdnsa.historystages.platform.bus.ICancellableEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Bonemeal about to be applied to a block. Cancelling means the plant does not grow and the
 * bonemeal is not used up. The player is null for a dispenser.
 */
public class BonemealEvent extends Event implements ICancellableEvent {

    @Nullable
    private final Player player;
    private final Level level;
    private final BlockPos pos;
    private final BlockState state;
    private final ItemStack stack;

    public BonemealEvent(@Nullable Player player, Level level, BlockPos pos, BlockState state, ItemStack stack) {
        this.player = player;
        this.level = level;
        this.pos = pos;
        this.state = state;
        this.stack = stack;
    }

    @Nullable
    public Player getPlayer() {
        return player;
    }

    public Level getLevel() {
        return level;
    }

    public BlockPos getPos() {
        return pos;
    }

    public BlockState getState() {
        return state;
    }

    public ItemStack getStack() {
        return stack;
    }
}
