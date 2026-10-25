package net.bananemdnsa.historystages.mixin.disguise;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bananemdnsa.historystages.events.lock.DisguiseBreakHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Where a player's break turns into drops. NeoForge has {@code BlockDropsEvent} for this; Forge
 * 1.20.1 does not, so the drop list is swapped at the one call every player break goes through.
 *
 * <p>The seven-argument {@code dropResources} is Forge's own, so it has no SRG name to map;
 * {@code getDrops} is vanilla and is remapped. Only the list changes — {@code spawnAfterBreak}
 * further down still sees the real block, as it does on NeoForge.</p>
 */
@Mixin(Block.class)
public class BlockDropsMixin {

    @WrapOperation(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;Z)V",
            remap = false,
            at = @At(value = "INVOKE", remap = true,
                    target = "Lnet/minecraft/world/level/block/Block;getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;"))
    private static List<ItemStack> historystages$disguiseDrops(BlockState state, ServerLevel level, BlockPos pos,
                                                               BlockEntity blockEntity, Entity entity, ItemStack tool,
                                                               Operation<List<ItemStack>> original) {
        List<ItemStack> disguised = DisguiseBreakHandler.disguisedDrops(state, level, pos, entity, tool);
        return disguised != null ? disguised : original.call(state, level, pos, blockEntity, entity, tool);
    }
}
