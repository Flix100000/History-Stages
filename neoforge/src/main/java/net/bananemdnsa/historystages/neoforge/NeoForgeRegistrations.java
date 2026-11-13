package net.bananemdnsa.historystages.neoforge;

import net.bananemdnsa.historystages.init.ModBlockEntities;
import net.bananemdnsa.historystages.platform.SimpleItemHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.DelegatingResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** Mod-bus registrations that only NeoForge has a hook for. */
final class NeoForgeRegistrations {

    private NeoForgeRegistrations() {}

    static void register(IEventBus modBus) {
        modBus.addListener(RegisterCapabilitiesEvent.class, NeoForgeRegistrations::registerCapabilities);
    }

    /**
     * What a pipe sees of the pedestal, as on main (ItemHandler.BLOCK). 26.2 replaced item handlers
     * with resource handlers; the container wrapper does not ask the handler before taking, so the
     * scroll lock is checked here, the same rule {@code SimpleItemHandler.extractItem} applies.
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.RESEARCH_PEDESTAL_BE.get(),
                (pedestal, side) -> locked(pedestal.getItemHandler()));
    }

    private static DelegatingResourceHandler<ItemResource> locked(SimpleItemHandler handler) {
        return new DelegatingResourceHandler<>(VanillaContainerWrapper.of(handler)) {
            @Override
            public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
                if (handler.extractItem(index, amount, true).isEmpty()) return 0;
                return super.extract(index, resource, amount, transaction);
            }

            // The slot-less form goes straight to the delegate otherwise, past the check above.
            @Override
            public int extract(ItemResource resource, int amount, TransactionContext transaction) {
                int taken = 0;
                for (int i = 0; i < size() && taken < amount; i++) {
                    taken += extract(i, resource, amount - taken, transaction);
                }
                return taken;
            }
        };
    }
}
