package net.bananemdnsa.historystages.fabric.mixin.event;

import net.bananemdnsa.historystages.fabric.events.ExperienceCapture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps a quiet {@code ExperienceCapture} from adding anything to the world. */
@Mixin(ServerLevel.class)
public abstract class QuietCaptureMixin {

    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void historystages$quiet(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (ExperienceCapture.quiet()) {
            entity.discard();
            cir.setReturnValue(false);
        }
    }
}
