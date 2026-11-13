package net.bananemdnsa.historystages.fabric.mixin.event;

import net.bananemdnsa.historystages.fabric.events.DeathDropCapture;
import net.bananemdnsa.historystages.fabric.events.Phases;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.EntityJoinLevelEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises the entity-joins-the-world event for fresh entities on the server. Cancelling keeps the
 * entity out. NeoForge also fires it for entities loaded from disk and on the client; the one
 * handler on main (MobSpawnLockHandler) is about new spawns.
 */
@Mixin(ServerLevel.class)
public abstract class EntityJoinLevelMixin {

    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void historystages$raise(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        // A dying entity's drops are held back for the drop event and added by LivingDropsMixin
        // afterwards. True keeps vanilla's bookkeeping happy: from its side the item did arrive.
        if (entity instanceof ItemEntity drop && DeathDropCapture.isActive()) {
            DeathDropCapture.capture(drop);
            cir.setReturnValue(true);
            return;
        }
        if (Phases.wanted(EntityJoinLevelEvent.class)
                && EventBus.post(new EntityJoinLevelEvent(entity, (ServerLevel) (Object) this)).isCanceled()) {
            cir.setReturnValue(false);
        }
    }
}
