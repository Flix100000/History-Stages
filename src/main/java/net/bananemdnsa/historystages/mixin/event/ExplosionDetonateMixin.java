package net.bananemdnsa.historystages.mixin.event;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.level.ExplosionEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Raises the detonation event while the block list is still open to change.
 *
 * <p>At the start of finalizeExplosion, which is where vanilla turns that list into missing
 * blocks. Handlers take entries out of it, so a zone keeps its own blocks while the explosion
 * still goes off everywhere else — cancelling the whole thing would also swallow the damage and
 * the sound, which nobody asked for.
 */
@Mixin(Explosion.class)
public abstract class ExplosionDetonateMixin {

    @Shadow
    public abstract List<BlockPos> getToBlow();

    @Shadow
    @Final
    private Level level;

    @Inject(method = "finalizeExplosion", at = @At("HEAD"))
    private void historystages$raise(boolean spawnParticles, CallbackInfo ci) {
        EventBus.post(new ExplosionEvent.Detonate(level, (Explosion) (Object) this, getToBlow()));
    }
}
