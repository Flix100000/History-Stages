package net.bananemdnsa.historystages.data.lock.engine;

import net.bananemdnsa.historystages.util.DebugLogger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import org.jetbrains.annotations.Nullable;

/**
 * What fluid, if any, an item stack is carrying.
 *
 * <p>The one place in the mod that names the fluid capability. NeoForge dropped
 * {@code FillBucketEvent}, so no event announces that someone is handling a fluid — the
 * capability is what is left, and it is also the better answer: every mod exposes a container's
 * contents through it, so one fluid entry covers the vanilla bucket, every modded bucket and
 * every tank item without a single item id being listed.
 *
 * <p>Reads the first tank only. Multi-tank items are rare, and where they exist the first tank
 * is the one the item is showing — the same fluid the player sees.
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
        IFluidHandlerItem handler = handlerOf(stack);
        if (handler == null || handler.getTanks() == 0) return null;

        FluidStack contents = handler.getFluidInTank(0);
        if (contents.isEmpty()) return null;

        ResourceLocation id = BuiltInRegistries.FLUID.getKey(contents.getFluid());
        return id != null ? id.toString() : null;
    }

    /**
     * Whether this stack can carry a fluid at all, full or empty.
     *
     * <p>The question {@link #of} cannot answer: an empty bucket holds nothing and is still the
     * thing a player scoops lava with.
     */
    public static boolean isContainer(@Nullable ItemStack stack) {
        return handlerOf(stack) != null;
    }

    /**
     * The one place that asks another mod about an item.
     *
     * <p>Whoever owns the item decides what comes back, and some of them read their own config to
     * decide — which throws outright while a world is still being opened (Sophisticated Backpacks,
     * #130). An item that will not answer holds nothing as far as the locks are concerned; taking
     * the game down over it is not proportionate.
     */
    @Nullable
    private static IFluidHandlerItem handlerOf(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        try {
            return stack.getCapability(Capabilities.FluidHandler.ITEM);
        } catch (Exception e) {
            ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
            DebugLogger.runtimeThrottled("Fluid Locks", "fluid-cap-" + item,
                    "Asking " + item + " what fluid it holds failed: " + e
                            + ". Treated as holding none.");
            return null;
        }
    }
}
