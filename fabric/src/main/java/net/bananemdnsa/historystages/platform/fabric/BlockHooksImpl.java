package net.bananemdnsa.historystages.platform.fabric;

import net.bananemdnsa.historystages.fabric.events.BreakingBlock;
import net.bananemdnsa.historystages.fabric.events.ExperienceCapture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Fabric side of {@code platform.BlockHooks}. */
public final class BlockHooksImpl {

    private BlockHooksImpl() {}

    public static float destroySpeed(Player player, BlockState state, BlockPos pos) {
        return BreakingBlock.around(player.level(), pos, () -> player.getDestroySpeed(state));
    }

    /** Quietly: only the amount is wanted, the block's other after-break work stays out of the world. */
    public static int experienceDrop(BlockState state, ServerLevel level, BlockPos pos,
                                     @Nullable BlockEntity blockEntity, @Nullable Entity breaker, ItemStack tool) {
        return ExperienceCapture.capture(() -> state.spawnAfterBreak(level, pos, tool, true), true);
    }
}
