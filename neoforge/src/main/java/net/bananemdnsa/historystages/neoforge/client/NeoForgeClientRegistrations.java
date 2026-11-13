package net.bananemdnsa.historystages.neoforge.client;

import net.bananemdnsa.historystages.init.ModMenuTypes;
import net.bananemdnsa.historystages.screen.ResearchPedestalScreen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only mod-bus registrations. Loaded only when running on a client. */
public final class NeoForgeClientRegistrations {

    private NeoForgeClientRegistrations() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterMenuScreensEvent.class,
                event -> event.register(ModMenuTypes.RESEARCH_MENU.get(), ResearchPedestalScreen::new));
    }
}
