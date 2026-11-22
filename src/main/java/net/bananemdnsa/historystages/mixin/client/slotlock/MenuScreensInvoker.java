package net.bananemdnsa.historystages.mixin.client.slotlock;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The screen a menu type opens, without MenuScreens also opening it. */
@Mixin(MenuScreens.class)
public interface MenuScreensInvoker {

    @Invoker("getConstructor")
    static <T extends AbstractContainerMenu> MenuScreens.ScreenConstructor<T, ?> historystages$getConstructor(MenuType<T> type) {
        throw new AssertionError();
    }
}
