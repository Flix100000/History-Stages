package net.bananemdnsa.historystages.fabric.mixin.event;

import net.bananemdnsa.historystages.fabric.events.Phases;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.EntityTravelToDimensionEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises the travel-to-dimension event at the start of {@code teleport}, as NeoForge does.
 * Cancelling leaves the entity where it is. Players need {@link ServerPlayerDimensionTravelMixin}
 * as well: {@code ServerPlayer.teleport} does not call this method (#143).
 */
@Mixin(Entity.class)
public abstract class DimensionTravelMixin {

    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/world/entity/Entity;",
            at = @At("HEAD"), cancellable = true)
    private void historystages$raise(TeleportTransition transition, CallbackInfoReturnable<Entity> cir) {
        if (Phases.wanted(EntityTravelToDimensionEvent.class) && EventBus.post(new EntityTravelToDimensionEvent(
                (Entity) (Object) this, transition.newLevel().dimension())).isCanceled()) {
            cir.setReturnValue(null);
        }
    }
}
