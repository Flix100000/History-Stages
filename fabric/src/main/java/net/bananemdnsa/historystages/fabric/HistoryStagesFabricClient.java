package net.bananemdnsa.historystages.fabric;

import net.fabricmc.api.ClientModInitializer;

public final class HistoryStagesFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricEventSources.registerClient();
        // Which screen belongs to the pedestal's menu; only the screen is client business.
        net.minecraft.client.gui.screens.MenuScreens.register(
                net.bananemdnsa.historystages.init.ModMenuTypes.RESEARCH_MENU.get(),
                net.bananemdnsa.historystages.screen.ResearchPedestalScreen::new);
    }
}
