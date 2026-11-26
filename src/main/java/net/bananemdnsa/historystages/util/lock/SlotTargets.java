package net.bananemdnsa.historystages.util.lock;

import net.bananemdnsa.historystages.mixin.slotlock.CompoundContainerAccessor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** Works out which slot-lock target a menu or a container answers to. */
public final class SlotTargets {

    private SlotTargets() {}

    /** Block id if a slot holds a block entity's container, else the menu type, else null. */
    @Nullable
    public static String resolve(AbstractContainerMenu menu) {
        boolean anyContainerSlot = false;
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory) continue;
            anyContainerSlot = true;
            Optional<String> block = blockOf(slot.container);
            if (block.isPresent()) return block.get();
        }
        if (!anyContainerSlot) return null;
        return menuTypeOf(menu);
    }

    @Nullable
    public static String menuTypeOf(AbstractContainerMenu menu) {
        try {
            ResourceLocation key = BuiltInRegistries.MENU.getKey(menu.getType());
            return key != null ? key.toString() : null;
        } catch (UnsupportedOperationException e) {
            // The player's own InventoryMenu has no type.
            return null;
        }
    }

    /** The block a container belongs to; a double chest answers with its halves' block. */
    public static Optional<String> blockOf(Container container) {
        if (container instanceof CompoundContainer compound) {
            return blockOf(((CompoundContainerAccessor) compound).historystages$first());
        }
        if (container instanceof BlockEntity be) {
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock());
            return Optional.of(key.toString());
        }
        return Optional.empty();
    }

    /** Never sent to a server; an editor preview only needs the slot layout. */
    private static final int PREVIEW_CONTAINER_ID = -100;

    /**
     * A block's menu for the editor preview, built from a fresh block entity with no level.
     * Null when the block has no menu. May throw for block entities that need a world.
     */
    @Nullable
    public static AbstractContainerMenu previewMenu(net.minecraft.world.level.block.Block block,
                                                    net.minecraft.world.entity.player.Player player) {
        if (!(block instanceof net.minecraft.world.level.block.EntityBlock entityBlock)) return null;
        BlockEntity be = entityBlock.newBlockEntity(net.minecraft.core.BlockPos.ZERO, block.defaultBlockState());
        if (!(be instanceof net.minecraft.world.MenuProvider provider)) return null;
        // Chests, barrels and shulker boxes count openers on startOpen and play a sound in their
        // level, which this one does not have. A removed block entity skips that.
        be.setRemoved();
        return provider.createMenu(PREVIEW_CONTAINER_ID, player.getInventory(), player);
    }

    @Nullable
    public static Inventory playerInventoryOf(AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory inventory) return inventory;
        }
        return null;
    }
}
