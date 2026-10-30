package net.bananemdnsa.historystages.mixin.disguise;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bananemdnsa.historystages.events.lock.DisguiseBreakHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A survival break of a disguised block drops what the disguise drops, and only with the tool the
 * disguise needs. Both calls get the disguise in place of the real state; the block entity stays
 * behind, since it belongs to the real block.
 *
 * <p>{@code playerDestroy} also pays out the experience, through the disguise's
 * {@code spawnAfterBreak}, which is where NeoForge's {@code getExpDrop} reads it from as well.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class DisguiseDropsMixin {

    @Shadow @Final protected ServerPlayer player;

    @WrapOperation(method = "destroyBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;hasCorrectToolForDrops(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean historystages$disguiseTool(ServerPlayer self, BlockState state, Operation<Boolean> original) {
        BlockState fake = DisguiseBreakHandler.breaksAs(self, state);
        return original.call(self, fake != null ? fake : state);
    }

    @WrapOperation(method = "destroyBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;playerDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/item/ItemStack;)V"))
    private void historystages$disguiseDrops(Block block, Level level, Player who, BlockPos pos, BlockState state,
                                             @Nullable BlockEntity blockEntity, ItemStack tool,
                                             Operation<Void> original) {
        BlockState fake = DisguiseBreakHandler.breaksAs(player, state);
        if (fake == null) {
            original.call(block, level, who, pos, state, blockEntity, tool);
        } else {
            original.call(fake.getBlock(), level, who, pos, fake, null, tool);
        }
    }
}
