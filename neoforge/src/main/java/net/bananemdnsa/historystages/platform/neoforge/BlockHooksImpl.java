package net.bananemdnsa.historystages.platform.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** NeoForge side of {@code platform.BlockHooks}. */
public final class BlockHooksImpl {

    private BlockHooksImpl() {}

    public static float destroySpeed(Player player, BlockState state, BlockPos pos) {
        return player.getDestroySpeed(state, pos);
    }

    public static int experienceDrop(BlockState state, ServerLevel level, BlockPos pos,
                                     @Nullable BlockEntity blockEntity, @Nullable Entity breaker, ItemStack tool) {
        return state.getExpDrop(level, pos, blockEntity, breaker, tool);
    }
}
