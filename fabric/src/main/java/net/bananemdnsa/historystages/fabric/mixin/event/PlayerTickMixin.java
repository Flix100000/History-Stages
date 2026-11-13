package net.bananemdnsa.historystages.fabric.mixin.event;

import net.bananemdnsa.historystages.fabric.events.Phases;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.tick.PlayerTickEvent;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Raises the player-tick event at the end of {@code Player.tick}, where NeoForge raises it, on
 * both sides. fabric-1.21.X looped over the server's players at the end of the server tick
 * instead, which moved the handlers behind every other entity's tick and never reached the
 * client player. {@code ServerPlayer.doTick} and {@code LocalPlayer.tick} both call this method.
 */
@Mixin(Player.class)
public abstract class PlayerTickMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void historystages$raise(CallbackInfo ci) {
        if (Phases.wanted(PlayerTickEvent.Post.class)) {
            EventBus.post(new PlayerTickEvent.Post((Player) (Object) this));
        }
    }
}
