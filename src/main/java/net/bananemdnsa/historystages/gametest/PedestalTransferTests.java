package net.bananemdnsa.historystages.gametest;

import java.lang.reflect.Field;

import net.bananemdnsa.historystages.block.entity.ResearchPedestalBlockEntity;
import net.bananemdnsa.historystages.init.ModBlocks;
import net.bananemdnsa.historystages.init.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;

/**
 * A pipe must not pull the scroll out of a pedestal whose research is running, and must be able
 * to once it is paused. Vanilla hoppers go through the block entity's Container methods; pipes
 * from other mods go through the transfer API, whose slot wrapper writes the slot directly and
 * never asked the handler's extractItem, where the lock lives.
 */
public class PedestalTransferTests {

    private static final BlockPos POS = new BlockPos(1, 2, 1);

    public PedestalTransferTests() {}

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void runningPedestalKeepsItsScroll(GameTestHelper helper) {
        ResearchPedestalBlockEntity pedestal = pedestalWithScroll(helper);
        setRunning(pedestal, true);
        long taken = extractScroll(helper);
        setRunning(pedestal, false);
        helper.assertTrue(taken == 0, "a pipe took the scroll while research was running");
        helper.assertTrue(!pedestal.getItemHandler().getStackInSlot(0).isEmpty(), "scroll slot emptied");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void pausedPedestalHandsOutItsScroll(GameTestHelper helper) {
        pedestalWithScroll(helper);
        helper.assertTrue(extractScroll(helper) == 1,
                "a paused pedestal refused the scroll, so the lock test proves nothing");
        helper.succeed();
    }

    private static ResearchPedestalBlockEntity pedestalWithScroll(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.RESEARCH_PEDESTAL.get());
        ResearchPedestalBlockEntity pedestal = helper.getBlockEntity(POS);
        pedestal.getItemHandler().setStackInSlot(0, new ItemStack(ModItems.RESEARCH_SCROLL.get()));
        return pedestal;
    }

    // What a pipe below the pedestal asks for: the storage on the pedestal's bottom face.
    private static long extractScroll(GameTestHelper helper) {
        Storage<ItemVariant> storage = ItemStorage.SIDED.find(helper.getLevel(), helper.absolutePos(POS), Direction.DOWN);
        helper.assertTrue(storage != null, "pedestal exposes no item storage");
        try (Transaction transaction = Transaction.openOuter()) {
            long taken = storage.extract(ItemVariant.of(ModItems.RESEARCH_SCROLL.get()), 1, transaction);
            transaction.commit();
            return taken;
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
