package net.bananemdnsa.historystages.neoforge;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.neoforge.gametest.NeoForgeGameTests;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(HistoryStages.MOD_ID)
public final class HistoryStagesNeoForge {
    public HistoryStagesNeoForge(IEventBus modBus) {
        HistoryStages.init();
        // Common setup runs on parallel workers; the registration window fires events into every
        // addon and must run on the main thread, as main's enqueueWork did.
        modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(HistoryStages::registrationWindow));
        NeoForgeEventSources.register(modBus);
        NeoForgeRegistrations.register(modBus);
        if (net.bananemdnsa.historystages.platform.Services.isModLoaded("curios")) {
            // Its own class, so Curios types are only linked when Curios is there.
            net.bananemdnsa.historystages.neoforge.compat.CuriosEquipLockHandler.register();
            org.slf4j.LoggerFactory.getLogger(HistoryStagesNeoForge.class).info("[HistoryStages] Curios integration loaded.");
        }
        if (net.bananemdnsa.historystages.platform.Services.isClient()) {
            net.bananemdnsa.historystages.neoforge.client.NeoForgeClientRegistrations.register(modBus);
        }
        if (!FMLEnvironment.isProduction()) {
            NeoForgeGameTests.register(modBus);
        }
    }
}
