package net.bananemdnsa.historystages.fabric.mixin.event;

import net.bananemdnsa.historystages.fabric.events.ExperienceCapture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets {@code ExperienceCapture} count a block's experience instead of spawning it. */
@Mixin(Block.class)
public abstract class ExperienceCaptureMixin {

    @Inject(method = "popExperience", at = @At("HEAD"), cancellable = true)
    private void historystages$capture(ServerLevel level, BlockPos pos, int amount, CallbackInfo ci) {
        if (ExperienceCapture.swallow(amount)) {
            ci.cancel();
        }
    }
}
