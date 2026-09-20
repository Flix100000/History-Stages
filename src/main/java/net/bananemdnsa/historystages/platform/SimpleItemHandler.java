package net.bananemdnsa.historystages.platform;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The pedestal's two slots.
 *
 * <p>Stands in for NeoForge's item handler, and matters for one reason beyond compiling: it writes
 * and reads <strong>the same NBT</strong>. A world that has been on the other loader keeps
 * whatever was in the pedestal, and one moved the other way does too. Anything else would empty
 * the slots of every pedestal in a save on the first load, silently.
 *
 * <p>It is also a {@link Container}, which is what lets vanilla's own slots and hoppers work with
 * it without a second wrapper. Extraction goes through {@link #extractItem} either way, so the
 * scroll lock cannot be walked around with a hopper.
 */
public class SimpleItemHandler implements Container {

    private final NonNullList<ItemStack> stacks;

    public SimpleItemHandler(int size) {
        this.stacks = NonNullList.withSize(size, ItemStack.EMPTY);
    }

    /** Called after anything in the handler changed. Does nothing here; subclasses react. */
    protected void onContentsChanged(int slot) {
    }

    public int getSlots() {
        return stacks.size();
    }

    public ItemStack getStackInSlot(int slot) {
        return stacks.get(slot);
    }

    public void setStackInSlot(int slot, ItemStack stack) {
        stacks.set(slot, stack);
        onContentsChanged(slot);
    }

    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || stacks.get(slot).isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack present = stacks.get(slot);
        int taken = Math.min(amount, present.getCount());
        if (simulate) {
            return present.copyWithCount(taken);
        }
        ItemStack result = present.split(taken);
        if (present.isEmpty()) {
            stacks.set(slot, ItemStack.EMPTY);
        }
        onContentsChanged(slot);
        return result;
    }

    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack present = stacks.get(slot);
        if (!present.isEmpty() && !ItemStack.isSameItemSameComponents(present, stack)) {
            return stack;
        }
        int limit = Math.min(getMaxStackSize(), stack.getMaxStackSize());
        int room = limit - present.getCount();
        if (room <= 0) {
            return stack;
        }
        int inserted = Math.min(room, stack.getCount());
        if (!simulate) {
            if (present.isEmpty()) {
                stacks.set(slot, stack.copyWithCount(inserted));
            } else {
                present.grow(inserted);
            }
            onContentsChanged(slot);
        }
        return inserted >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - inserted);
    }

    // ------------------------------------------------------------------ nbt

    /** NeoForge's shape: the slot count, and one entry per filled slot carrying its index. */
    public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        ListTag items = new ListTag();
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag entry = (CompoundTag) stack.save(registries, new CompoundTag());
            entry.putInt("Slot", i);
            items.add(entry);
        }
        CompoundTag tag = new CompoundTag();
        tag.put("Items", items);
        tag.putInt("Size", stacks.size());
        return tag;
    }

    public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
        stacks.clear();
        ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag entry = items.getCompound(i);
            int slot = entry.getInt("Slot");
            if (slot < 0 || slot >= stacks.size()) {
                // A save from a build with more slots than this one. Dropping the entry is the
                // only honest answer; keeping it would need a slot that does not exist.
                continue;
            }
            ItemStack.parse(registries, entry).ifPresent(stack -> stacks.set(slot, stack));
        }
    }

    // ------------------------------------------------------------ container

    @Override
    public int getContainerSize() {
        return stacks.size();
    }

    @Override
    public boolean isEmpty() {
        return stacks.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return getStackInSlot(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        // Through extractItem rather than the list, so whatever a subclass refuses there is
        // refused to hoppers as well.
        return extractItem(slot, amount, false);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(stacks, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        setStackInSlot(slot, stack);
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        stacks.clear();
    }
}
