package net.bananemdnsa.historystages.fabric;

import net.bananemdnsa.historystages.client.editor.tab.ClientCategoryEditorSetup;
import net.fabricmc.api.ClientModInitializer;

public final class HistoryStagesFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricEventSources.registerClient();
        // Which screen belongs to the pedestal's menu; only the screen is client business.
        net.minecraft.client.gui.screens.MenuScreens.register(
                net.bananemdnsa.historystages.init.ModMenuTypes.RESEARCH_MENU.get(),
                net.bananemdnsa.historystages.screen.ResearchPedestalScreen::new);
        // Architectury fires CLIENT_SETUP right after SETUP (the common registration window), from
        // its own client entrypoint. Calling the editor window directly here could run before
        // SETUP, depending on which mod's client entrypoint Fabric calls first.
        dev.architectury.event.events.client.ClientLifecycleEvent.CLIENT_SETUP.register(
                minecraft -> ClientCategoryEditorSetup.run());
    }
}
