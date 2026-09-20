package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.commands.ClientDebugCommand;
import net.bananemdnsa.historystages.client.screen.ResearchPedestalScreen;
import net.bananemdnsa.historystages.init.ModMenuTypes;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class HistoryStagesFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientNetworking.register();
        ClientDisconnectHandler.register();
        ClientDebugCommand.register();
        TooltipHandler.register();
        EditorButtonHandler.register();
        MenuScreens.register(ModMenuTypes.RESEARCH_MENU, ResearchPedestalScreen::new);
    }
}
