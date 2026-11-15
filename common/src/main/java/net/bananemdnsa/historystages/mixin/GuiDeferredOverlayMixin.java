package net.bananemdnsa.historystages.mixin;

import net.bananemdnsa.historystages.client.editor.widget.DeferredOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the editor's queued popups once a screen is done, before vanilla's own tooltip, so a
 * tooltip still ends up above an open dropdown.
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiDeferredOverlayMixin {

    @Inject(method = "extractDeferredElements", at = @At("HEAD"))
    private void historystages$drawDeferredOverlays(int mouseX, int mouseY, float a, CallbackInfo ci) {
        DeferredOverlay.flush((GuiGraphicsExtractor) (Object) this);
    }
}
