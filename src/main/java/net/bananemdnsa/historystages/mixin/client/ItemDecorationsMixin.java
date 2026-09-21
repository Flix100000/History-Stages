package net.bananemdnsa.historystages.mixin.client;

import net.bananemdnsa.historystages.client.LockIconRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the lock icon over a locked item in the ordinary inventory screens.
 *
 * <p>NeoForge has a registry of item decorators and calls them from here. There is no such
 * registry on this loader, so the drawing hangs off the method the decorators were called from —
 * which keeps it in the same place in the frame, over the stack count and the durability bar
 * rather than under them.
 *
 * <p>Only the five-argument form is hooked. The shorter one delegates to it, so hooking both
 * would draw the icon twice on every slot that shows a count.
 *
 * <p>EMI's own ingredient panel does not come through here and is covered by its own mixin, so
 * there is no double draw there either.
 */
@Mixin(GuiGraphics.class)
public abstract class ItemDecorationsMixin {

    @Inject(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at = @At("TAIL"))
    private void historystages$drawLockIcon(Font font, ItemStack stack, int x, int y, String text,
                                            CallbackInfo ci) {
        ResourceLocation icon = LockIconRenderer.iconFor(stack);
        if (icon != null) {
            LockIconRenderer.draw((GuiGraphics) (Object) this, icon, x, y);
        }
    }
}
