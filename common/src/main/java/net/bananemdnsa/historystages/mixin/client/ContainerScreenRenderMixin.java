package net.bananemdnsa.historystages.mixin.client;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.client.ContainerScreenEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Raises the container screen's background and foreground events on both loaders. NeoForge 26.2
 * dropped its own {@code ContainerScreenEvent}, and Fabric never had one.
 *
 * <p>Background: right after the screen's own background, in screen coordinates, before any slot
 * is drawn. Foreground: after the slots and their highlight, still inside the pose the screen
 * translated to its top-left corner, so slot coordinates can be used as they are; anything drawn
 * there that has to cover the items needs a new stratum first.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenRenderMixin {

    @Inject(method = "extractContents", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            shift = At.Shift.AFTER))
    private void historystages$background(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
                                          CallbackInfo ci) {
        if (EventBus.hasListeners(ContainerScreenEvent.Render.Background.class)) {
            EventBus.post(new ContainerScreenEvent.Render.Background(
                    (AbstractContainerScreen<?>) (Object) this, graphics, mouseX, mouseY));
        }
    }

    @Inject(method = "extractContents", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractSlotHighlightFront(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
            shift = At.Shift.AFTER))
    private void historystages$foreground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
                                          CallbackInfo ci) {
        if (EventBus.hasListeners(ContainerScreenEvent.Render.Foreground.class)) {
            EventBus.post(new ContainerScreenEvent.Render.Foreground(
                    (AbstractContainerScreen<?>) (Object) this, graphics, mouseX, mouseY));
        }
    }
}
