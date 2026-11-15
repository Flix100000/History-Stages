package net.bananemdnsa.historystages.mixin.event;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.EntityTeleportEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Raises the pearl-landing event, so a zone can refuse the spot a pearl would put someone in
 * without refusing teleports in general. Fired where NeoForge fires it: on the server, for a
 * player thrower whose connection is live, once vanilla has decided the owner may be teleported.
 *
 * <p>A cancel answers "may not teleport", which sends vanilla down its own branch for that case:
 * the pearl is discarded, the player stays put. Cancelling at the head of {@code onHit} instead
 * left the pearl alive, landing again every tick and keeping its owner's chunks loaded.
 */
@Mixin(ThrownEnderpearl.class)
public abstract class EnderPearlTeleportMixin {

    @WrapOperation(method = "onHit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/ThrownEnderpearl;isAllowedToTeleportOwner(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/Level;)Z"))
    private boolean historystages$raise(Entity owner, Level level, Operation<Boolean> original) {
        boolean allowed = original.call(owner, level);
        if (!allowed || !(owner instanceof ServerPlayer player) || !player.connection.isAcceptingMessages()) {
            return allowed;
        }
        ThrownEnderpearl pearl = (ThrownEnderpearl) (Object) this;
        return !EventBus.post(new EntityTeleportEvent.EnderPearl(player, pearl.getX(), pearl.getY(), pearl.getZ()))
                .isCanceled();
    }
}
