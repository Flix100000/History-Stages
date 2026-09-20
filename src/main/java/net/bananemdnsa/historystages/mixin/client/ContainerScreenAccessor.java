package net.bananemdnsa.historystages.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches the top-left corner of a container screen.
 *
 * <p>NeoForge adds getters for these two; vanilla keeps them protected. Anything drawn over
 * somebody else's container screen has to be placed relative to them, because a screen is
 * centred and the window is not.
 */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {

    @Accessor("leftPos")
    int historystages$getLeftPos();

    @Accessor("topPos")
    int historystages$getTopPos();
}
