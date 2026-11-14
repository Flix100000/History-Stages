package net.bananemdnsa.historystages.fabric.mixin.event;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.bananemdnsa.historystages.fabric.events.BreakingBlock;
import net.bananemdnsa.historystages.fabric.events.Phases;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.player.PlayerEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Raises the break-speed event around the whole method, so it starts from the speed vanilla worked
 * out (tools, effects). A lock answers zero: the swing and the cracks stay, the break never finishes.
 *
 * <p>Not an {@code @Inject} at RETURN: Architectury's own mixin injects there too and always sets
 * the return value, which ends the method before any injector ordered after it. Which one came
 * first changed from launch to launch.
 */
@Mixin(Player.class)
public abstract class BreakSpeedMixin {

    @WrapMethod(method = "getDestroySpeed")
    private float historystages$raise(BlockState state, Operation<Float> original) {
        float speed = original.call(state);
        if (!Phases.wanted(PlayerEvent.BreakSpeed.class)) {
            return speed;
        }
        PlayerEvent.BreakSpeed event = EventBus.post(
                new PlayerEvent.BreakSpeed((Player) (Object) this, state, speed, BreakingBlock.pos()));
        return event.getNewSpeed();
    }
}
