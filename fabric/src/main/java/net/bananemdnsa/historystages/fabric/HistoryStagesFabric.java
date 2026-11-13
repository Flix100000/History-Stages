package net.bananemdnsa.historystages.fabric;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.init.ModBlocks;
import net.fabricmc.api.ModInitializer;
import net.bananemdnsa.historystages.init.ModItems;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.registry.FuelValueEvents;

public final class HistoryStagesFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        HistoryStages.init();
        FabricEventSources.register();
        registerFlammability();
        registerFuel();
        // What a pipe sees of the pedestal (main: NeoForge item handler capability).
        net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.registerForBlockEntity(
                (pedestal, direction) -> net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage.of(pedestal.getItemHandler(), direction),
                net.bananemdnsa.historystages.init.ModBlockEntities.RESEARCH_PEDESTAL_BE.get());
    }

    // NeoForge reads the shelves' burn time from data/neoforge/data_maps/item/furnace_fuels.json;
    // Fabric builds fuel values in code. Same 300 ticks, wooden shelves only.
    private static void registerFuel() {
        FuelValueEvents.BUILD.register((builder, context) -> ModItems.SCROLL_SHELF_ITEMS.forEach((wood, item) -> {
            if (wood.flammable()) builder.add(item.get(), 300);
        }));
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
