package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.api.client.ClientStagesChangedEvent;
import net.bananemdnsa.historystages.client.cache.ClientIndividualStageCache;
import net.bananemdnsa.historystages.client.cache.ClientStageCache;
import net.bananemdnsa.historystages.events.StageDefinitionsEventBridge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Posts the client-side api events from the client tick: {@link ClientStagesChangedEvent}, and
 * the client half of {@link StageDefinitionsEventBridge} for a client on a remote server.
 *
 * <p>Compared by version counter rather than posted from the sync packets, so a listener always
 * sees both caches settled and the packet handlers stay free of api concerns.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID, value = Dist.CLIENT)
public final class ClientApiEvents {

    private static int seenGlobal = -1;
    private static int seenIndividual = -1;

    private ClientApiEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        StageDefinitionsEventBridge.postIfChanged();

        int global = ClientStageCache.version();
        int individual = ClientIndividualStageCache.version();
        if (global != seenGlobal || individual != seenIndividual) {
            seenGlobal = global;
            seenIndividual = individual;
            NeoForge.EVENT_BUS.post(new ClientStagesChangedEvent());
        }
    }
}
