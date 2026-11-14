package net.bananemdnsa.historystages.fabric.mixin.event;

import net.bananemdnsa.historystages.fabric.events.BreakingBlock;
import net.bananemdnsa.historystages.fabric.events.Phases;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.player.PlayerEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's harvest check, at the one place vanilla decides it: both the break speed divisor and
 * the server's "does this break drop anything" go through here.
 */
@Mixin(Player.class)
public abstract class HarvestCheckMixin {

    @Inject(method = "hasCorrectToolForDrops", at = @At("RETURN"), cancellable = true)
    private void historystages$raise(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (!Phases.wanted(PlayerEvent.HarvestCheck.class)) {
            return;
        }
        Player player = (Player) (Object) this;
        BlockGetter level = BreakingBlock.level() != null ? BreakingBlock.level() : player.level();
        PlayerEvent.HarvestCheck event = EventBus.post(new PlayerEvent.HarvestCheck(player, state, level,
                BreakingBlock.pos(), cir.getReturnValueZ()));
        if (event.canHarvest() != cir.getReturnValueZ()) {
            cir.setReturnValue(event.canHarvest());
        }
    }
}
