package net.bananemdnsa.historystages.fabric;

import net.bananemdnsa.historystages.HistoryStages;
import net.fabricmc.api.ModInitializer;

public final class HistoryStagesFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        HistoryStages.init();
    }
}
