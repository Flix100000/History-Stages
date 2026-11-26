package net.bananemdnsa.historystages.mixin.client;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.client.ContainerScreenEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Raises the container screen events where NeoForge does: the background one once the texture is
 * down and before any slot is drawn, the foreground one after the labels, while the pose is still
 * moved to the GUI's corner.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenRenderMixin {

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            shift = At.Shift.AFTER))
    private void historystages$background(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick,
                                          CallbackInfo ci) {
        EventBus.post(new ContainerScreenEvent.Render.Background(
                (AbstractContainerScreen<?>) (Object) this, guiGraphics, mouseX, mouseY));
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderLabels(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            shift = At.Shift.AFTER))
    private void historystages$foreground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick,
                                          CallbackInfo ci) {
        EventBus.post(new ContainerScreenEvent.Render.Foreground(
                (AbstractContainerScreen<?>) (Object) this, guiGraphics, mouseX, mouseY));
    }
}
