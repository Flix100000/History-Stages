package net.bananemdnsa.historystages.mixin.disguise;

import net.bananemdnsa.historystages.client.disguise.ClientDisguises;
import net.bananemdnsa.historystages.data.disguise.Disguises;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * A disguised item is drawn as its disguise, in hand, in slots and on the ground. Every item
 * render resolves its model here.
 */
@Mixin(ItemModelResolver.class)
public class ItemModelResolverMixin {

    @ModifyVariable(method = "appendItemLayers", at = @At("HEAD"), argsOnly = true)
    private ItemStack historystages$disguiseModel(ItemStack stack) {
        Disguises.Resolved r = ClientDisguises.forStack(stack);
        return r == null ? stack : new ItemStack(r.target(), stack.getCount());
    }
}
