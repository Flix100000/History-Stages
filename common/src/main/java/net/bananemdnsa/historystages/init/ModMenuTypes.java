package net.bananemdnsa.historystages.init;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.screen.ResearchPedestalMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.IContainerFactory;
import net.bananemdnsa.historystages.platform.DeferredRegister;
import net.bananemdnsa.historystages.platform.DeferredHolder;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, HistoryStages.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<ResearchPedestalMenu>> RESEARCH_MENU =
            registerMenuType(ResearchPedestalMenu::new, "research_menu");

    private static <T extends AbstractContainerMenu> DeferredHolder<MenuType<?>, MenuType<T>> registerMenuType(IContainerFactory<T> factory, String name) {
        return MENUS.register(name, () -> IMenuTypeExtension.create(factory));
    }

    public static void register() {
        MENUS.register();
    }
}
