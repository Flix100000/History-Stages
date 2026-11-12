package net.bananemdnsa.historystages.neoforge;

import net.bananemdnsa.historystages.neoforge.events.ClientBridge;
import net.bananemdnsa.historystages.neoforge.events.EntityBridge;
import net.bananemdnsa.historystages.neoforge.events.LevelBridge;
import net.bananemdnsa.historystages.neoforge.events.PlayerBridge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Feeds NeoForge's events into the platform bus.
 *
 * <p>The handlers in common listen on the platform bus, with the event classes NeoForge 1.21.1 had.
 * For each of those a listener here takes the real NeoForge 26.2 event, builds the platform event,
 * posts it, and writes cancel flags and results back, so the handlers decide about the real thing.
 */
public final class NeoForgeEventSources {

    private NeoForgeEventSources() {}

    public static void register(IEventBus modBus) {
        IEventBus game = NeoForge.EVENT_BUS;
        PlayerBridge.register(game);
        EntityBridge.register(game);
        LevelBridge.register(game, modBus);
        // Behind the dist check, so a dedicated server never loads a class that names client types.
        if (FMLEnvironment.getDist().isClient()) {
            ClientBridge.register(game);
        }
    }
}
