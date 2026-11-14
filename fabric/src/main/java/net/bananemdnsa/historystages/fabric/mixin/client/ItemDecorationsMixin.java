package net.bananemdnsa.historystages.fabric.mixin.client;

import net.bananemdnsa.historystages.client.LockIconRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the lock icon over a locked item in the ordinary inventory screens.
 *
 * <p>NeoForge has a registry of item decorators and calls them from here. Fabric has none, so the
 * drawing hangs off the method the decorators were called from, after the count and durability bar.
 *
 * <p>Only the five-argument form is hooked. The shorter one delegates to it, so hooking both
 * would draw the icon twice.
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class ItemDecorationsMixin {

    @Inject(method = "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at = @At("TAIL"))
    private void historystages$drawLockIcon(Font font, ItemStack stack, int x, int y, String countText,
                                            CallbackInfo ci) {
        Identifier icon = LockIconRenderer.iconFor(stack);
        if (icon != null) {
            LockIconRenderer.draw((GuiGraphicsExtractor) (Object) this, icon, x, y);
        }
    }
}
