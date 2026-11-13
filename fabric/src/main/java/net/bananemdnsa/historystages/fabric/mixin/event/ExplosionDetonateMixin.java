package net.bananemdnsa.historystages.fabric.mixin.event;

import java.util.ArrayList;
import java.util.List;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bananemdnsa.historystages.fabric.events.Phases;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.level.ExplosionEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Raises the detonation event with the block list while it can still change, right after vanilla
 * works it out and before entities are hurt or blocks removed (NeoForge raises it at the start of
 * {@code hurtEntities} with the same list). Handlers take protected blocks out of it; the explosion
 * still goes off everywhere else.
 */
@Mixin(ServerExplosion.class)
public abstract class ExplosionDetonateMixin {

    @WrapOperation(method = "explode", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/ServerExplosion;calculateExplodedPositions()Ljava/util/List;"))
    private List<BlockPos> historystages$raise(ServerExplosion self, Operation<List<BlockPos>> original) {
        List<BlockPos> blocks = original.call(self);
        if (!Phases.wanted(ExplosionEvent.Detonate.class)) {
            return blocks;
        }
        List<BlockPos> open = new ArrayList<>(blocks);
        EventBus.post(new ExplosionEvent.Detonate(self.level(), self, open));
        return open;
    }
}
