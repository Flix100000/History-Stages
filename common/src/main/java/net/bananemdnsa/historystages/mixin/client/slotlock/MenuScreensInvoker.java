package net.bananemdnsa.historystages.mixin.client.slotlock;

import java.util.Map;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The registered screen factories. Since 26.x their interface is private, so the map is read here
 * and the factory is called reflectively (see {@code PreviewMenus}).
 */
@Mixin(MenuScreens.class)
public interface MenuScreensInvoker {

    @Accessor("SCREENS")
    static Map<MenuType<?>, ?> historystages$screens() {
        throw new AssertionError();
    }
}
