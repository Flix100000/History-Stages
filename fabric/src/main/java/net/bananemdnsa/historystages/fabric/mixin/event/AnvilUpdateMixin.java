package net.bananemdnsa.historystages.fabric.mixin.event;

import net.bananemdnsa.historystages.fabric.events.Phases;
import net.bananemdnsa.historystages.fabric.mixin.ItemCombinerMenuAccessor;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.AnvilUpdateEvent;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Raises the anvil-result event after vanilla worked out the result, where NeoForge 26.2 raises it
 * (its patched {@code createResult} runs vanilla first, then the hook, and only with a left item).
 * A cancel clears the result, the level cost and the material cost, as NeoForge does: the gated
 * combination produces nothing and the screen does not keep showing the old cost.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilUpdateMixin {

    @Shadow
    @Final
    private DataSlot cost;

    @Shadow
    private int repairItemCountCost;

    @Inject(method = "createResult", at = @At("RETURN"))
    private void historystages$raise(CallbackInfo ci) {
        ItemCombinerMenuAccessor menu = (ItemCombinerMenuAccessor) this;
        Container inputs = menu.historystages$getInputSlots();
        if (inputs.getItem(0).isEmpty() || !Phases.wanted(AnvilUpdateEvent.class)) {
            return;
        }
        AnvilUpdateEvent event = EventBus.post(new AnvilUpdateEvent(
                inputs.getItem(0), inputs.getItem(1), menu.historystages$getPlayer()));
        if (event.isCanceled()) {
            menu.historystages$getResultSlots().setItem(0, ItemStack.EMPTY);
            cost.set(0);
            repairItemCountCost = 0;
        }
    }
}
