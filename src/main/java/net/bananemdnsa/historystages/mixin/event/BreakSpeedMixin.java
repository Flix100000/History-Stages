package net.bananemdnsa.historystages.mixin.event;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.player.PlayerEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises the break-speed event and hands vanilla back whatever the handlers decided.
 *
 * <p>At RETURN rather than HEAD so the event starts from the speed vanilla worked out, tools and
 * effects included. A lock answers zero here instead of cancelling the break, which leaves the
 * swing and the cracks in place and simply never finishes.
 */
@Mixin(Player.class)
public abstract class BreakSpeedMixin {

    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void historystages$raise(BlockState state, CallbackInfoReturnable<Float> cir) {
        PlayerEvent.BreakSpeed event = EventBus.post(
                new PlayerEvent.BreakSpeed((Player) (Object) this, state, cir.getReturnValue()));
        if (event.getNewSpeed() != event.getOriginalSpeed()) {
            cir.setReturnValue(event.getNewSpeed());
        }
    }
}
