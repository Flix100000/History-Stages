package net.bananemdnsa.historystages.mixin.client.slotlock;

import net.bananemdnsa.historystages.client.slotlock.PreviewMenus;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While the slot picker draws a container's background, the screen's own backdrop (the darkened,
 * blurred world) stays out: the picker only wants the container texture, which 26.2 draws in the
 * same method right after calling this one.
 */
@Mixin(Screen.class)
public abstract class PreviewBackgroundMixin {

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void historystages$skipBackdrop(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
                                            CallbackInfo ci) {
        if (PreviewMenus.drawingPreview()) ci.cancel();
    }
}
