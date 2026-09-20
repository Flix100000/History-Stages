package net.bananemdnsa.historystages.mixin.event;

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
 * Raises the block-placed event after the fact, which is what the auto-trigger for placing a
 * block listens to. The state is read back from the level rather than from the item, so it is the
 * block as it actually ended up, facing and all.
 */
@Mixin(BlockItem.class)
public abstract class BlockPlaceMixin {

    @Inject(method = "place", at = @At("RETURN"))
    private void historystages$raise(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (!cir.getReturnValue().consumesAction() || context.getPlayer() == null) {
            return;
        }
        EventBus.post(new BlockEvent.EntityPlaceEvent(
                context.getLevel(), context.getClickedPos(),
                context.getLevel().getBlockState(context.getClickedPos()), context.getPlayer()));
    }
}
