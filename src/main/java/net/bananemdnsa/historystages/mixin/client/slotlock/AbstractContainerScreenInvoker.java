package net.bananemdnsa.historystages.mixin.client.slotlock;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the slot picker draw a container screen's background and labels and nothing else. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenInvoker {

    @Invoker("renderBg")
    void historystages$renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY);

    @Invoker("renderLabels")
    void historystages$renderLabels(GuiGraphics g, int mouseX, int mouseY);
}
