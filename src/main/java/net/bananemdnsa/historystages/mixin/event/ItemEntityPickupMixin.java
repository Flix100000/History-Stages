package net.bananemdnsa.historystages.mixin.event;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.player.ItemEntityPickupEvent;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Raises both halves of the item-pickup event: the one that can refuse, and the one that reports.
 *
 * <p>The second is decided by whether the item entity is gone afterwards, which is what vanilla
 * does when a stack is fully taken. A partial pickup leaves it in the world and is not reported,
 * matching what the trigger behind this is asking about.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityPickupMixin {

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void historystages$refuse(Player player, CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        ItemEntityPickupEvent.Pre event = EventBus.post(new ItemEntityPickupEvent.Pre(player, self));
        if (event.canPickup() == TriState.FALSE) {
            ci.cancel();
        }
    }

    @Inject(method = "playerTouch", at = @At("RETURN"))
    private void historystages$report(Player player, CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.isRemoved()) {
            EventBus.post(new ItemEntityPickupEvent.Post(player, self));
        }
    }
}
