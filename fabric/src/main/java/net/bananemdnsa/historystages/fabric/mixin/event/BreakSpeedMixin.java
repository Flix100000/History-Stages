package net.bananemdnsa.historystages.fabric.mixin.event;

import net.bananemdnsa.historystages.fabric.events.Phases;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.player.PlayerEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises the break-speed event at RETURN, so it starts from the speed vanilla worked out (tools,
 * effects). A lock answers zero: the swing and the cracks stay, the break never finishes.
 */
@Mixin(Player.class)
public abstract class BreakSpeedMixin {

    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void historystages$raise(BlockState state, CallbackInfoReturnable<Float> cir) {
        if (!Phases.wanted(PlayerEvent.BreakSpeed.class)) {
            return;
        }
        PlayerEvent.BreakSpeed event = EventBus.post(
                new PlayerEvent.BreakSpeed((Player) (Object) this, state, cir.getReturnValue()));
        if (event.getNewSpeed() != event.getOriginalSpeed()) {
            cir.setReturnValue(event.getNewSpeed());
        }
    }
}
