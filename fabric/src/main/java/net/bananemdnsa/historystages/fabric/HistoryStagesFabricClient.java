package net.bananemdnsa.historystages.fabric;

import net.fabricmc.api.ClientModInitializer;

public final class HistoryStagesFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricEventSources.registerClient();
    }
}
