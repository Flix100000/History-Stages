package net.bananemdnsa.historystages.data.lock.engine;

import net.bananemdnsa.historystages.util.DebugLogger;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * What fluid, if any, an item stack is carrying.
 *
 * <p>The one place in the mod that asks another mod what is inside its container. No event
 * announces that someone is handling a fluid, and asking the item is the better answer anyway:
 * every mod exposes a container's contents this way, so one fluid entry covers the vanilla
 * bucket, every modded bucket and every tank item without a single item id being listed.
 *
 * <p>Reads the first tank only. Multi-tank items are rare, and where they exist the first tank is
 * the one the item is showing — the same fluid the player sees.
 */
public final class FluidContent {

    private FluidContent() {}

    /**
     * The registry id of the fluid this stack holds, or null when it holds none.
     *
     * <p>Null for an empty bucket, which is why taking a fluid <em>out of the world</em> cannot
     * be answered here and has a handler of its own.
     */
    @Nullable
    public static String of(@Nullable ItemStack stack) {
        Storage<FluidVariant> storage = storageOf(stack);
        if (storage == null) {
            return null;
        }
        for (StorageView<FluidVariant> tank : storage) {
            // The first one and no further, so this reports what the item is showing rather than
            // hunting through a backpack for something to name.
            if (tank.isResourceBlank() || tank.getAmount() <= 0) {
                return null;
            }
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(tank.getResource().getFluid());
            return id != null ? id.toString() : null;
        }
        return null;
    }

    /**
     * Whether this stack can carry a fluid at all, full or empty.
     *
     * <p>The question {@link #of} cannot answer: an empty bucket holds nothing and is still the
     * thing a player scoops lava with.
     */
    public static boolean isContainer(@Nullable ItemStack stack) {
        return storageOf(stack) != null;
    }

    /**
     * The one place that asks another mod about an item.
     *
     * <p>Whoever owns the item decides what comes back, and some of them read their own config to
     * decide — which throws outright while a world is still being opened (Sophisticated
     * Backpacks, #130). An item that will not answer holds nothing as far as the locks are
     * concerned; taking the game down over it is not proportionate.
     *
     * <p>A constant context, because this only ever reads. One that could write back would need a
     * real inventory slot to write into, and there is none here.
     */
    @Nullable
    private static Storage<FluidVariant> storageOf(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        try {
            return FluidStorage.ITEM.find(stack, ContainerItemContext.withConstant(stack));
        } catch (Exception e) {
            ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
            DebugLogger.runtimeThrottled("Fluid Locks", "fluid-cap-" + item,
                    "Asking " + item + " what fluid it holds failed: " + e
                            + ". Treated as holding none.");
            return null;
        }
    }
}
