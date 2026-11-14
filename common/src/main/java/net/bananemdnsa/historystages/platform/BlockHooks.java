package net.bananemdnsa.historystages.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Block-breaking questions that NeoForge answers with its own extensions. */
public final class BlockHooks {

    private BlockHooks() {}

    /**
     * How fast the player breaks {@code state} at {@code pos}, after every mod has had its say
     * (NeoForge: {@code Player.getDestroySpeed(state, pos)}, which fires its break-speed event;
     * Fabric: vanilla's, which our mixin raises the same event from).
     */
    @ExpectPlatform
    public static float destroySpeed(Player player, BlockState state, BlockPos pos) {
        throw new AssertionError();
    }

    /**
     * The experience {@code state} would drop when broken like this, without spawning anything
     * (NeoForge: {@code getExpDrop}). Fabric works it out by running the block's own break
     * side-effects with experience captured instead of spawned.
     */
    @ExpectPlatform
    public static int experienceDrop(BlockState state, ServerLevel level, BlockPos pos,
                                     @Nullable BlockEntity blockEntity, @Nullable Entity breaker, ItemStack tool) {
        throw new AssertionError();
    }
}
