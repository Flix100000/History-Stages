package net.bananemdnsa.historystages.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

/**
 * Fluid containers as the loader sees them. Stands in for NeoForge's fluid capabilities and
 * {@code CauldronFluidContent}; on Fabric the Transfer API answers. Callers catch exceptions
 * thrown by containers of other mods themselves.
 */
public final class Fluids {

    private Fluids() {}

    /** Whether the stack exposes a fluid container at all, full or empty. */
    @ExpectPlatform
    public static boolean isItemContainer(ItemStack stack) {
        throw new AssertionError();
    }

    /** The fluid in the stack's first tank, or null when it has no container or that tank is empty. */
    @ExpectPlatform
    public static @Nullable Fluid firstItemFluid(ItemStack stack) {
        throw new AssertionError();
    }

    /** Whether this block is a cauldron the loader knows a fluid for. */
    @ExpectPlatform
    public static boolean isFluidCauldron(Block block) {
        throw new AssertionError();
    }

    /** The fluid a filled cauldron holds, or null when it is empty or no fluid cauldron. */
    @ExpectPlatform
    public static @Nullable Fluid cauldronFluid(BlockState state) {
        throw new AssertionError();
    }

    /** Whether the block at this position exposes a fluid container. */
    @ExpectPlatform
    public static boolean hasBlockContainer(Level level, BlockPos pos) {
        throw new AssertionError();
    }

    /** The fluid's display name (NeoForge fluid type description, Fabric fluid variant name). */
    @ExpectPlatform
    public static net.minecraft.network.chat.Component nameOf(Fluid fluid) {
        throw new AssertionError();
    }

    /** The fluid of a loader fluid stack (NeoForge {@code FluidStack}, Fabric {@code FluidVariant}), else null. */
    @ExpectPlatform
    public static @Nullable Fluid fluidOfLoaderStack(Object subject) {
        throw new AssertionError();
    }
}
