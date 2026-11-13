package net.bananemdnsa.historystages.platform.fabric;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.CauldronFluidContent;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

/** Fabric side of {@code platform.Fluids}, over the Transfer API. */
public final class FluidsImpl {

    private FluidsImpl() {}

    private static @Nullable Storage<FluidVariant> itemStorage(ItemStack stack) {
        if (stack.isEmpty()) return null;
        // Constant context: this only reads, there is no slot to write back into.
        return FluidStorage.ITEM.find(stack, ContainerItemContext.withConstant(stack));
    }

    public static boolean isItemContainer(ItemStack stack) {
        return itemStorage(stack) != null;
    }

    public static @Nullable Fluid firstItemFluid(ItemStack stack) {
        Storage<FluidVariant> storage = itemStorage(stack);
        if (storage == null) return null;
        for (StorageView<FluidVariant> tank : storage) {
            // First tank only, the one the item is showing.
            if (tank.isResourceBlank() || tank.getAmount() <= 0) return null;
            return tank.getResource().getFluid();
        }
        return null;
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
        return FluidStorage.SIDED.find(level, pos, null) != null;
    }

    public static net.minecraft.network.chat.Component nameOf(Fluid fluid) {
        return net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes.getName(FluidVariant.of(fluid));
    }

    public static @Nullable Fluid fluidOfLoaderStack(Object subject) {
        return subject instanceof FluidVariant variant ? variant.getFluid() : null;
    }
}
