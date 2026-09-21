package net.bananemdnsa.historystages.mixin.event;

import net.bananemdnsa.historystages.mixin.ItemCombinerMenuAccessor;
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
 * Raises the anvil-result event before vanilla works out what the two inputs would make.
 *
 * <p>Cancelling empties the output slot instead of hiding the anvil, which is how a locked
 * enchantment simply stops being applicable: the player can still rename, still repair with
 * anything else, and only the gated combination produces nothing.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilUpdateMixin {

    @Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
    private void historystages$raise(CallbackInfo ci) {
        // Through an accessor on the superclass: these three are declared there, and a shadow on
        // the anvil compiles happily and then fails when the game starts.
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
