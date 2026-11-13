package net.bananemdnsa.historystages.fabric;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.init.ModBlocks;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;

public final class HistoryStagesFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        HistoryStages.init();
        FabricEventSources.register();
        registerFlammability();
    }

    // Same fire behaviour as the chiseled bookshelf; nether wood shelves never catch. NeoForge
    // asks the block itself (ScrollShelfBlock.getFlammability), Fabric a table.
    private static void registerFlammability() {
        ModBlocks.SCROLL_SHELVES.forEach((wood, shelf) -> {
            if (wood.flammable()) {
                FlammableBlockRegistry.getDefaultInstance().add(shelf.get(), 30, 20);
            }
        });
    }
}
