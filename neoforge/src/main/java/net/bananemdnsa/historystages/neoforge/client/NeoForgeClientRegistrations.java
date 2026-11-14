package net.bananemdnsa.historystages.neoforge.client;

import net.bananemdnsa.historystages.client.editor.tab.ClientCategoryEditorSetup;
import net.bananemdnsa.historystages.init.ModMenuTypes;
import net.bananemdnsa.historystages.screen.ResearchPedestalScreen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.bananemdnsa.historystages.client.WorldOverlayRenderTypes;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only mod-bus registrations. Loaded only when running on a client. */
public final class NeoForgeClientRegistrations {

    private NeoForgeClientRegistrations() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterMenuScreensEvent.class,
                event -> event.register(ModMenuTypes.RESEARCH_MENU.get(), ResearchPedestalScreen::new));
        // Preloaded with vanilla's, so a broken pipeline fails the shader load instead of a later draw.
        modBus.addListener(RegisterRenderPipelinesEvent.class,
                event -> WorldOverlayRenderTypes.ownPipelines().forEach(event::registerPipeline));
        // One decorator for every item: whether a stack is locked is only known per stack.
        LockDecorator decorator = new LockDecorator();
        modBus.addListener(RegisterItemDecorationsEvent.class,
                event -> net.minecraft.core.registries.BuiltInRegistries.ITEM.forEach(item -> event.register(item, decorator)));
        // Client setup comes after common setup, whose queued registration window has frozen the
        // lock categories an editor tab is attached to.
        modBus.addListener(FMLClientSetupEvent.class,
                event -> event.enqueueWork(ClientCategoryEditorSetup::run));
    }
}
