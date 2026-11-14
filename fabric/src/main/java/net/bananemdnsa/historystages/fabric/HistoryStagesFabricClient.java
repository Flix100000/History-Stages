package net.bananemdnsa.historystages.fabric;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.editor.tab.ClientCategoryEditorSetup;
import net.bananemdnsa.historystages.client.editor.tab.ClientRegistrationWindow;
import net.bananemdnsa.historystages.client.WorldOverlayRenderTypes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.ResourceReloaderKeys;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

public final class HistoryStagesFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricEventSources.registerClient();
        // Which screen belongs to the pedestal's menu; only the screen is client business.
        net.minecraft.client.gui.screens.MenuScreens.register(
                net.bananemdnsa.historystages.init.ModMenuTypes.RESEARCH_MENU.get(),
                net.bananemdnsa.historystages.screen.ResearchPedestalScreen::new);
        // Fabric has no pipeline registry. Compiling ours right after every shader load (which also
        // empties the pipeline cache) puts a broken one in the log even if no overlay is ever drawn.
        // Earlier, e.g. at CLIENT_STARTED, the shader sources are not loaded yet.
        Identifier precompile = HistoryStages.location("overlay_pipelines");
        var loader = ResourceLoader.get(PackType.CLIENT_RESOURCES);
        loader.registerReloadListener(precompile,
                (ResourceManagerReloadListener) manager -> WorldOverlayRenderTypes.precompile());
        loader.addListenerOrdering(ResourceReloaderKeys.Client.SHADERS, precompile);
        // The editor window needs the common one (Architectury SETUP) behind it. Architectury fires
        // SETUP and CLIENT_SETUP from its own client entrypoint, which may run before or after this one.
        ClientRegistrationWindow.openAfterCommonWindow(HistoryStages.registrationThread() != null,
                window -> dev.architectury.event.events.client.ClientLifecycleEvent.CLIENT_SETUP.register(
                        minecraft -> window.run()),
                ClientCategoryEditorSetup::run);
    }
}
