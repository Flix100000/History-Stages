package net.bananemdnsa.historystages.mixin.disguise;

import net.bananemdnsa.historystages.client.disguise.ClientDisguises;
import net.bananemdnsa.historystages.data.disguise.Disguises;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Draws a disguised item as its disguise — inventory, hand, ground, frames and recipe viewers all
 * fetch the model here. {@code render} is swapped too, so a disguise with a custom renderer (a
 * chest, a shield) is drawn by its own renderer rather than the hidden item's.
 */
@Mixin(ItemRenderer.class)
public class ItemRendererMixin {

    @ModifyVariable(method = "getModel", at = @At("HEAD"), argsOnly = true)
    private ItemStack historystages$disguiseModel(ItemStack stack) {
        return historystages$swap(stack);
    }

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true)
    private ItemStack historystages$disguiseRender(ItemStack stack) {
        return historystages$swap(stack);
    }

    private static ItemStack historystages$swap(ItemStack stack) {
        Disguises.Resolved r = ClientDisguises.forStack(stack);
        return r == null ? stack : new ItemStack(r.target(), stack.getCount());
    }
}
