package net.bananemdnsa.historystages.mixin.event;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.level.BlockGrowFeatureEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Raises the grow-feature event before a mushroom turns into a huge one, where NeoForge does. */
@Mixin(MushroomBlock.class)
public abstract class MushroomGrowFeatureMixin {

    @Inject(method = "growMushroom", at = @At("HEAD"), cancellable = true)
    private void historystages$raise(ServerLevel level, BlockPos pos, BlockState state, RandomSource random,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (EventBus.post(new BlockGrowFeatureEvent(level, random, pos, null)).isCanceled()) {
            cir.setReturnValue(false);
        }
    }
}
