package net.bananemdnsa.historystages.client.slotlock;

import net.bananemdnsa.historystages.mixin.ContainerScreenSizeAccessor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

/** The container screen getters NeoForge 1.21.1 had (getGuiLeft, getSlotUnderMouse, ...). */
public final class ContainerScreens {

    private ContainerScreens() {}

    public static int left(AbstractContainerScreen<?> screen) {
        return ((ContainerScreenSizeAccessor) screen).historystages$leftPos();
    }

    public static int top(AbstractContainerScreen<?> screen) {
        return ((ContainerScreenSizeAccessor) screen).historystages$topPos();
    }

    public static int imageWidth(AbstractContainerScreen<?> screen) {
        return ((ContainerScreenSizeAccessor) screen).historystages$imageWidth();
    }

    public static int imageHeight(AbstractContainerScreen<?> screen) {
        return ((ContainerScreenSizeAccessor) screen).historystages$imageHeight();
    }

    public static @Nullable Slot slotUnderMouse(AbstractContainerScreen<?> screen) {
        return ((ContainerScreenSizeAccessor) screen).historystages$hoveredSlot();
    }
}
