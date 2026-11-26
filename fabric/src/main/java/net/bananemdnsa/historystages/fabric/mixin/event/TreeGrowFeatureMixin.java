package net.bananemdnsa.historystages.fabric.mixin.event;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.level.BlockGrowFeatureEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises the grow-feature event for saplings and azaleas, where NeoForge does. Every route ends
 * here — random tick, bonemeal, or a mod's growth accelerator — so a locked sapling stays a
 * sapling however it is pushed.
 */
@Mixin(TreeGrower.class)
public abstract class TreeGrowFeatureMixin {

    @Inject(method = "growTree", at = @At("HEAD"), cancellable = true)
    private void historystages$raise(ServerLevel level, ChunkGenerator generator, BlockPos pos,
                                     BlockState state, RandomSource random, CallbackInfoReturnable<Boolean> cir) {
        if (EventBus.post(new BlockGrowFeatureEvent(level, random, pos, null)).isCanceled()) {
            cir.setReturnValue(false);
        }
    }
}
