package net.bananemdnsa.historystages.mixin.event;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.EntityTravelToDimensionEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises the change-dimension event. Cancelling leaves the entity where it is, so the portal keeps
 * working for everyone else and simply does nothing for whoever is gated.
 */
@Mixin(Entity.class)
public abstract class DimensionTravelMixin {

    @Inject(method = "changeDimension", at = @At("HEAD"), cancellable = true)
    private void historystages$raise(DimensionTransition transition, CallbackInfoReturnable<Entity> cir) {
        EntityTravelToDimensionEvent event = EventBus.post(new EntityTravelToDimensionEvent(
                (Entity) (Object) this, transition.newLevel().dimension()));
        if (event.isCanceled()) {
            cir.setReturnValue(null);
        }
    }
}
