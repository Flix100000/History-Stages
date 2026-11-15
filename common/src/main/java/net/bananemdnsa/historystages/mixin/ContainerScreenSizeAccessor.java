package net.bananemdnsa.historystages.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The image size became final, but the pedestal grows and shrinks with its requirements panel.
 * JEI and other overlays read these fields to keep clear of the screen.
 */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenSizeAccessor {
    @Mutable
    @Accessor("imageWidth")
    void historystages$setImageWidth(int width);

    @Mutable
    @Accessor("imageHeight")
    void historystages$setImageHeight(int height);
}
