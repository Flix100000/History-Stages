package net.bananemdnsa.historystages.fabric.gametest;

import java.lang.reflect.Field;

import net.bananemdnsa.historystages.block.entity.ResearchPedestalBlockEntity;
import net.bananemdnsa.historystages.init.ModBlocks;
import net.bananemdnsa.historystages.init.ModItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;

/**
 * A pipe or hopper must not pull the scroll out of a pedestal whose research is running, and must
 * be able to once it is paused (main: the item handler capability refused it in extractItem).
 */
public class PedestalTransferTests {

    private static final BlockPos POS = new BlockPos(1, 2, 1);

    @GameTest(skyAccess = true)
    public void runningPedestalKeepsItsScroll(GameTestHelper helper) {
        ResearchPedestalBlockEntity pedestal = pedestalWithScroll(helper);
        setRunning(pedestal, true);
        helper.assertTrue(extractScroll(helper) == 0, "a pipe took the scroll while research was running");
        helper.assertTrue(!pedestal.getItemHandler().getStackInSlot(0).isEmpty(), "scroll slot emptied");
        helper.succeed();
    }

    @GameTest(skyAccess = true)
    public void pausedPedestalHandsOutItsScroll(GameTestHelper helper) {
        pedestalWithScroll(helper);
        helper.assertTrue(extractScroll(helper) == 1, "a paused pedestal refused the scroll, so the lock test proves nothing");
        helper.succeed();
    }

    private static ResearchPedestalBlockEntity pedestalWithScroll(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.RESEARCH_PEDESTAL.get());
        ResearchPedestalBlockEntity pedestal = helper.getBlockEntity(POS, ResearchPedestalBlockEntity.class);
        pedestal.getItemHandler().setStackInSlot(0, new ItemStack(ModItems.RESEARCH_SCROLL.get()));
        return pedestal;
    }

    // What a hopper below the pedestal asks for: the storage on the pedestal's bottom face.
    private static long extractScroll(GameTestHelper helper) {
        Storage<ItemVariant> storage = ItemStorage.SIDED.find(helper.getLevel(), helper.absolutePos(POS), Direction.DOWN);
        helper.assertTrue(storage != null, "pedestal exposes no item storage");
        try (Transaction transaction = Transaction.openOuter()) {
            return storage.extract(ItemVariant.of(ModItems.RESEARCH_SCROLL.get()), 1, transaction);
        }
    }

    private static void setRunning(ResearchPedestalBlockEntity pedestal, boolean running) {
        try {
            Field field = ResearchPedestalBlockEntity.class.getDeclaredField("running");
            field.setAccessible(true);
            field.setBoolean(pedestal, running);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
