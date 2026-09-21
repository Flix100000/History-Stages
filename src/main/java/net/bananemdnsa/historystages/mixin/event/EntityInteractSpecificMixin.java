package net.bananemdnsa.historystages.mixin.event;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises the aimed-at-a-spot form of entity interaction.
 *
 * <p>Vanilla asks this one first and some entities answer it differently from the general form —
 * an armour stand decides which piece to swap from where it was clicked. Fabric only has a
 * callback for the general form, so a lock relying on that alone would miss every interaction
 * these entities answer here.
 */
@Mixin(Entity.class)
public abstract class EntityInteractSpecificMixin {

    @Inject(method = "interactAt", at = @At("HEAD"), cancellable = true)
    private void historystages$raise(Player player, Vec3 localPos, InteractionHand hand,
                                     CallbackInfoReturnable<InteractionResult> cir) {
        PlayerInteractEvent.EntityInteractSpecific event = EventBus.post(
                new PlayerInteractEvent.EntityInteractSpecific(player, hand, (Entity) (Object) this, localPos));
        if (event.isCanceled()) {
            cir.setReturnValue(event.getCancellationResult());
        }
    }
}
