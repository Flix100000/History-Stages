package net.bananemdnsa.historystages.fabric;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.platform.SimpleItemHandler;
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedSlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.Direction;

/**
 * What a pipe or hopper sees of the pedestal. The container wrapper edits the slot stacks
 * directly and never calls {@link SimpleItemHandler#extractItem}, so the scroll lock is asked
 * here, per slot, the same way the NeoForge module does it.
 */
final class PedestalStorage {

    private PedestalStorage() {}

    static Storage<ItemVariant> of(SimpleItemHandler handler, Direction side) {
        ContainerStorage container = ContainerStorage.of(handler, side);
        List<SingleSlotStorage<ItemVariant>> slots = new ArrayList<>();
        for (int i = 0; i < container.getSlotCount(); i++) {
            slots.add(new GuardedSlot(handler, i, container.getSlot(i)));
        }
        return new CombinedSlottedStorage<>(slots);
    }

    private record GuardedSlot(SimpleItemHandler handler, int index, SingleSlotStorage<ItemVariant> delegate)
            implements SingleSlotStorage<ItemVariant> {

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            if (handler.extractItem(index, 1, true).isEmpty()) return 0;
            return delegate.extract(resource, maxAmount, transaction);
        }

        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            return delegate.insert(resource, maxAmount, transaction);
        }

        @Override
        public boolean supportsInsertion() {
            return delegate.supportsInsertion();
        }

        @Override
        public boolean supportsExtraction() {
            return delegate.supportsExtraction();
        }

        @Override
        public boolean isResourceBlank() {
            return delegate.isResourceBlank();
        }

        @Override
        public ItemVariant getResource() {
            return delegate.getResource();
        }

        @Override
        public long getAmount() {
            return delegate.getAmount();
        }

        @Override
        public long getCapacity() {
            return delegate.getCapacity();
        }

        @Override
        public StorageView<ItemVariant> getUnderlyingView() {
            return delegate.getUnderlyingView();
        }

        @Override
        public long getVersion() {
            return delegate.getVersion();
        }
    }
}
