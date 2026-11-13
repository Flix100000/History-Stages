package net.bananemdnsa.historystages.platform.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.CauldronFluidContent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jetbrains.annotations.Nullable;

/** NeoForge side of {@code platform.Fluids}. */
public final class FluidsImpl {

    private FluidsImpl() {}

    private static @Nullable ResourceHandler<FluidResource> itemHandler(ItemStack stack) {
        if (stack.isEmpty()) return null;
        // Read-only look on a copy, so nothing the handler does can reach the real stack.
        return ItemAccess.forStack(stack.copy()).getCapability(Capabilities.Fluid.ITEM);
    }

    public static boolean isItemContainer(ItemStack stack) {
        return itemHandler(stack) != null;
    }

    public static @Nullable Fluid firstItemFluid(ItemStack stack) {
        ResourceHandler<FluidResource> handler = itemHandler(stack);
        if (handler == null || handler.size() == 0) return null;
        FluidResource resource = handler.getResource(0);
        if (resource.isEmpty() || handler.getAmountAsLong(0) <= 0) return null;
        return resource.getFluid();
    }

    public static boolean isFluidCauldron(Block block) {
        return CauldronFluidContent.getForBlock(block) != null;
    }

    public static @Nullable Fluid cauldronFluid(BlockState state) {
        CauldronFluidContent content = CauldronFluidContent.getForBlock(state.getBlock());
        if (content == null || content.currentLevel(state) <= 0) return null;
        return content.fluid;
    }

    public static boolean hasBlockContainer(Level level, BlockPos pos) {
        return level.getCapability(Capabilities.Fluid.BLOCK, pos, null) != null;
    }

    public static @Nullable Fluid fluidOfLoaderStack(Object subject) {
        if (subject instanceof FluidStack stack) return stack.getFluid();
        if (subject instanceof FluidResource resource) return resource.getFluid();
        return null;
    }
}
