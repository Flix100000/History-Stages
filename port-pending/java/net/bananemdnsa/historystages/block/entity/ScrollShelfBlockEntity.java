package net.bananemdnsa.historystages.block.entity;

import net.bananemdnsa.historystages.block.ScrollShelfBlock;
import net.bananemdnsa.historystages.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the six scrolls of a {@link ScrollShelfBlock}. Mirrors the chiseled bookshelf's block
 * entity, with one addition: the contents are synced to clients, because Jade names the scroll
 * under the crosshair from the client side.
 */
public class ScrollShelfBlockEntity extends BlockEntity implements Container {
    private final NonNullList<ItemStack> items = NonNullList.withSize(ScrollShelfBlock.SLOTS, ItemStack.EMPTY);
    private int lastInteractedSlot = -1;

    public ScrollShelfBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCROLL_SHELF_BE.get(), pos, state);
    }

    private void updateState(int slot) {
        if (slot < 0 || slot >= ScrollShelfBlock.SLOTS || level == null) return;
        lastInteractedSlot = slot;
        setChanged();
        // A scroll swapped for another leaves the state as it was, and with it no update to the
        // client, whose copy Jade reads.
        if (!syncSlotStates()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /**
     * Brings the block state's occupied slots in line with the contents. Needed on its own after
     * a shelf is placed from an item that carries scrolls: those are copied straight into the
     * list, past {@link #setItem}, and would otherwise sit in slots that render empty.
     *
     * @return whether the state changed
     */
    public boolean syncSlotStates() {
        if (level == null) return false;
        BlockState current = getBlockState();
        BlockState state = current;
        for (int i = 0; i < ScrollShelfBlock.SLOTS; i++) {
            state = state.setValue(ScrollShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(i), !items.get(i).isEmpty());
        }
        if (state == current) return false;
        level.setBlock(worldPosition, state, 3);
        level.gameEvent(GameEvent.BLOCK_CHANGE, worldPosition, GameEvent.Context.of(state));
        return true;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        lastInteractedSlot = tag.getInt("last_interacted_slot");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, true, registries);
        tag.putInt("last_interacted_slot", lastInteractedSlot);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public int getContainerSize() {
        return ScrollShelfBlock.SLOTS;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = items.get(slot);
        items.set(slot, ItemStack.EMPTY);
        if (!stack.isEmpty()) updateState(slot);
        return stack;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return removeItem(slot, 1);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (stack.is(ScrollShelfBlock.SCROLLS)) {
            items.set(slot, stack);
            updateState(slot);
        } else if (stack.isEmpty()) {
            removeItem(slot, 1);
        }
    }

    @Override
    public boolean canTakeItem(Container target, int slot, ItemStack stack) {
        return target.hasAnyMatching(other -> other.isEmpty()
                || ItemStack.isSameItemSameComponents(stack, other)
                && other.getCount() + stack.getCount() <= target.getMaxStackSize(other));
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.is(ScrollShelfBlock.SCROLLS) && getItem(slot).isEmpty() && stack.getCount() == getMaxStackSize();
    }

    public int getLastInteractedSlot() {
        return lastInteractedSlot;
    }

    // Keeps the contents on a shelf picked with ctrl+middle-click in creative, like vanilla.
    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        input.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("Items");
    }
}
