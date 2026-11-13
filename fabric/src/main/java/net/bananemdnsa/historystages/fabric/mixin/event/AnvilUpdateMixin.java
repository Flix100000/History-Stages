package net.bananemdnsa.historystages.fabric.mixin.event;

import net.bananemdnsa.historystages.fabric.events.Phases;
import net.bananemdnsa.historystages.fabric.mixin.ItemCombinerMenuAccessor;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.AnvilUpdateEvent;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Raises the anvil-result event where NeoForge does, at the start of {@code createResult}.
 * Cancelling empties the output slot: the gated combination produces nothing, renaming and other
 * repairs still work.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilUpdateMixin {

    @Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
    private void historystages$raise(CallbackInfo ci) {
        if (!Phases.wanted(AnvilUpdateEvent.class)) {
            return;
        }
        ItemCombinerMenuAccessor menu = (ItemCombinerMenuAccessor) this;
        Container inputs = menu.historystages$getInputSlots();
        AnvilUpdateEvent event = EventBus.post(new AnvilUpdateEvent(
                inputs.getItem(0), inputs.getItem(1), menu.historystages$getPlayer()));
        if (event.isCanceled()) {
            menu.historystages$getResultSlots().setItem(0, ItemStack.EMPTY);
            ci.cancel();
        }
    }
}
