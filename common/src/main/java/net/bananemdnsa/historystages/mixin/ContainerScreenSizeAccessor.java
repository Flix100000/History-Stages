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

    // Getters NeoForge 1.21.1 added to the screen itself; vanilla keeps these protected.
    @Accessor("leftPos")
    int historystages$leftPos();

    @Accessor("topPos")
    int historystages$topPos();

    @Accessor("imageWidth")
    int historystages$imageWidth();

    @Accessor("imageHeight")
    int historystages$imageHeight();

    @Accessor("hoveredSlot")
    net.minecraft.world.inventory.Slot historystages$hoveredSlot();
}
