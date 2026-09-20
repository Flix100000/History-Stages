package net.bananemdnsa.historystages.init;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.platform.DeferredHolder;
import net.bananemdnsa.historystages.platform.DeferredRegister;
import net.bananemdnsa.historystages.screen.ResearchPedestalMenu;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, HistoryStages.MOD_ID);

    /**
     * The client side of the menu needs the pedestal's position to find its block entity, and a
     * plain MenuType carries nothing beyond the window id. NeoForge covers that with a menu type
     * that writes an untyped buffer; here the extra payload has a type, and the only thing in it
     * is the position, so it travels as a BlockPos.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<ResearchPedestalMenu>> RESEARCH_MENU =
            MENUS.register("research_menu",
                    () -> new ExtendedScreenHandlerType<>(ResearchPedestalMenu::new, BlockPos.STREAM_CODEC));

    public static void register() {
        MENUS.register();
    }
}
