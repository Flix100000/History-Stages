package net.bananemdnsa.historystages.fabric.mixin.event;

import net.bananemdnsa.historystages.fabric.events.Phases;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.EntityTravelToDimensionEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The player half of {@link DimensionTravelMixin} (PR #143, Issue #141). {@code ServerPlayer}
 * overrides {@code teleport} without calling the entity version, so without this the dimension
 * lock never stops a player on Fabric. The descriptor names the real override, not the bridge
 * method that returns {@code Entity}.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDimensionTravelMixin {

    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
            at = @At("HEAD"), cancellable = true)
    private void historystages$raise(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> cir) {
        if (Phases.wanted(EntityTravelToDimensionEvent.class) && EventBus.post(new EntityTravelToDimensionEvent(
                (ServerPlayer) (Object) this, transition.newLevel().dimension())).isCanceled()) {
            cir.setReturnValue(null);
        }
    }
}
