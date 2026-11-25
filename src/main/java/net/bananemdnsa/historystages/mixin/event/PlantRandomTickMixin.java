package net.bananemdnsa.historystages.mixin.event;

import net.bananemdnsa.historystages.data.lock.engine.StageLocks;
import net.bananemdnsa.historystages.platform.PlantHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Raises the crop-grow event for random ticks.
 *
 * <p>NeoForge patches about a dozen growing blocks to ask before they grow. Here every random tick
 * passes one place, so a single hook covers those blocks and modded plants alike. A locked plant
 * skips the tick; a plant's random tick does nothing but grow, so nothing else is lost.
 *
 * <p>Random ticks are among the hottest paths on a server, and most packs lock no plants. That
 * check comes first, so such a pack pays one lookup per tick and builds no event.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class PlantRandomTickMixin {

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void historystages$raiseCropGrow(ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (!StageLocks.engine().anyCropLocks()) return;
        if (!PlantHooks.canCropGrow(level, pos, (BlockState) (Object) this)) ci.cancel();
    }
}
