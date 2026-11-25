package net.bananemdnsa.historystages.mixin.event;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.level.BlockGrowFeatureEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FungusBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Raises the grow-feature event before a fungus turns into a huge one, where NeoForge does. */
@Mixin(FungusBlock.class)
public abstract class FungusGrowFeatureMixin {

    @Inject(method = "performBonemeal", at = @At("HEAD"), cancellable = true)
    private void historystages$raise(ServerLevel level, RandomSource random, BlockPos pos, BlockState state,
                                     CallbackInfo ci) {
        if (EventBus.post(new BlockGrowFeatureEvent(level, random, pos, null)).isCanceled()) {
            ci.cancel();
        }
    }
}
