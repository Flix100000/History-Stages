package net.bananemdnsa.historystages.mixin.toast;

import net.bananemdnsa.historystages.client.toast.ToastLayout;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Overrides the y translation of a rendering toast with the flush-stacked offset computed by
 * {@link ToastComponentMixin}, replacing the vanilla {@code index * 32} grid position.
 */
@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastManager$ToastInstance")
public abstract class ToastInstanceMixin {

    @Shadow @Final private int firstSlotIndex;

    @ModifyArg(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;I)V",
            at = @At(value = "INVOKE",
                    target = "Lorg/joml/Matrix3x2fStack;translate(FF)Lorg/joml/Matrix3x2f;"),
            index = 1)
    private float historystages$tightStackY(float originalY) {
        return ToastLayout.offset(this.firstSlotIndex);
    }
}
