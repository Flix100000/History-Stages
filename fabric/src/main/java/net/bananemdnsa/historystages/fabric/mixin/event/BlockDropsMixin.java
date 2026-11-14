package net.bananemdnsa.historystages.fabric.mixin.event;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.fabric.events.ExperienceCapture;
import net.bananemdnsa.historystages.fabric.events.Phases;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.level.BlockDropsEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's block-drops event, built the way NeoForge builds it in the same method: loot as item
 * entities, the block's own after-break work with its experience held back, then the event, then
 * whatever is left goes into the world. Only the breaker-and-tool form fires it, as on NeoForge.
 */
@Mixin(Block.class)
public abstract class BlockDropsMixin {

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), cancellable = true)
    private static void historystages$raise(BlockState state, Level level, BlockPos pos, @Nullable BlockEntity blockEntity,
                                            @Nullable Entity breaker, ItemStack tool, CallbackInfo ci) {
        if (!(level instanceof ServerLevel serverLevel) || !Phases.wanted(BlockDropsEvent.class)) {
            return;
        }
        ci.cancel();
        List<ItemEntity> drops = new ArrayList<>();
        for (ItemStack stack : Block.getDrops(state, serverLevel, pos, blockEntity, breaker, tool)) {
            if (!stack.isEmpty()) drops.add(itemEntity(serverLevel, pos, stack));
        }
        int experience = ExperienceCapture.capture(() -> state.spawnAfterBreak(serverLevel, pos, tool, true));
        BlockDropsEvent event = EventBus.post(new BlockDropsEvent(serverLevel, pos, state, blockEntity, drops,
                breaker, tool, experience));
        if (event.isCanceled() || !serverLevel.getGameRules().get(GameRules.BLOCK_DROPS)) {
            return;
        }
        for (ItemEntity drop : event.getDrops()) {
            serverLevel.addFreshEntity(drop);
        }
        if (event.getDroppedExperience() > 0) {
            ExperienceOrb.award(serverLevel, Vec3.atCenterOf(pos), event.getDroppedExperience());
        }
    }

    /** Placed the way {@code Block.popResource} places its drops. */
    private static ItemEntity itemEntity(ServerLevel level, BlockPos pos, ItemStack stack) {
        double half = EntityTypes.ITEM.getHeight() / 2.0;
        double x = pos.getX() + 0.5 + Mth.nextDouble(level.getRandom(), -0.25, 0.25);
        double y = pos.getY() + 0.5 + Mth.nextDouble(level.getRandom(), -0.25, 0.25) - half;
        double z = pos.getZ() + 0.5 + Mth.nextDouble(level.getRandom(), -0.25, 0.25);
        ItemEntity entity = new ItemEntity(level, x, y, z, stack);
        entity.setDefaultPickUpDelay();
        return entity;
    }
}
