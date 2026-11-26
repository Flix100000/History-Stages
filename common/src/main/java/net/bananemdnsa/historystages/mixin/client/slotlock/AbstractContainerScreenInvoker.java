package net.bananemdnsa.historystages.mixin.client.slotlock;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** For the slot picker's preview: draws another screen's labels without the rest of it. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenInvoker {

    @Invoker("extractLabels")
    void historystages$extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY);
}
