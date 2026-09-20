package net.bananemdnsa.historystages.mixin.event;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.EntityJoinLevelEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises the entity-joins-the-world event. Cancelling keeps the entity out.
 *
 * <p>On the server level rather than the common one: this is the last gate a spawn passes, and
 * the client only ever adds entities it was told about, where refusing one would desynchronise it.
 */
@Mixin(ServerLevel.class)
public abstract class EntityJoinLevelMixin {

    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void historystages$raise(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        EntityJoinLevelEvent event = EventBus.post(
                new EntityJoinLevelEvent(entity, (ServerLevel) (Object) this));
        if (event.isCanceled()) {
            cir.setReturnValue(false);
        }
    }
}
