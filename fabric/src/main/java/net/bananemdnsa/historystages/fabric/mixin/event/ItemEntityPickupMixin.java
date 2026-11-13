package net.bananemdnsa.historystages.fabric.mixin.event;

import net.bananemdnsa.historystages.fabric.events.Phases;
import net.bananemdnsa.historystages.platform.TriState;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.player.ItemEntityPickupEvent;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Raises both halves of the item-pickup event: the one that can refuse, and the one that reports.
 * Server side only, where NeoForge raises them too (vanilla returns early on the client).
 *
 * <p>The report is decided by whether the item entity is gone afterwards, which is what vanilla
 * does when a stack is fully taken. A partial pickup is not reported.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityPickupMixin {

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void historystages$refuse(Player player, CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.level().isClientSide() || !Phases.wanted(ItemEntityPickupEvent.Pre.class)) {
            return;
        }
        if (EventBus.post(new ItemEntityPickupEvent.Pre(player, self)).canPickup() == TriState.FALSE) {
            ci.cancel();
        }
    }

    @Inject(method = "playerTouch", at = @At("RETURN"))
    private void historystages$report(Player player, CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (!self.level().isClientSide() && self.isRemoved() && Phases.wanted(ItemEntityPickupEvent.Post.class)) {
            EventBus.post(new ItemEntityPickupEvent.Post(player, self));
        }
    }
}
