package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.client.editor.tab.ClientCategoryEditorSetup;
import net.bananemdnsa.historystages.init.ModMenuTypes;
import net.bananemdnsa.historystages.screen.ResearchPedestalScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.MenuScreens;

/**
 * The client half of startup.
 *
 * <p>Runs after the common initializer, which is what the editor registrations depend on: a lock
 * category has to exist before a tab can be attached to it.
 */
@Environment(EnvType.CLIENT)
public class HistoryStagesFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPacketHandler.register();
        ClientEventSources.register();
        ClientHandlers.register();

        // Which screen belongs to the pedestal's menu. The menu type itself was registered on the
        // common side; only the screen is client business.
        MenuScreens.register(ModMenuTypes.RESEARCH_MENU.get(), ResearchPedestalScreen::new);

        // The window in which an addon gives its own additions a face in the editor.
        ClientCategoryEditorSetup.run();
    }
}
