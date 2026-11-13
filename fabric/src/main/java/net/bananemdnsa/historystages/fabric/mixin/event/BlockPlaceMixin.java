package net.bananemdnsa.historystages.fabric.mixin.event;

import net.bananemdnsa.historystages.fabric.events.Phases;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.level.BlockEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises the block-placed event after the fact, for the place-a-block auto trigger. The state is
 * read back from the level, so it is the block as it ended up.
 *
 * <p>A cancel is not acted on: NeoForge undoes the placement from a block snapshot, vanilla has
 * none. The only handler on main (AutoTriggerEventBridge) never cancels.
 */
@Mixin(BlockItem.class)
public abstract class BlockPlaceMixin {

    @Inject(method = "place", at = @At("RETURN"))
    private void historystages$raise(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (!cir.getReturnValue().consumesAction() || context.getPlayer() == null
                || !Phases.wanted(BlockEvent.EntityPlaceEvent.class)) {
            return;
        }
        EventBus.post(new BlockEvent.EntityPlaceEvent(context.getLevel(), context.getClickedPos(),
                context.getLevel().getBlockState(context.getClickedPos()), context.getPlayer()));
    }
}
