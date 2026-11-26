package net.bananemdnsa.historystages.gametest;

import net.bananemdnsa.historystages.data.lock.SlotLockMode;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Mod inserts against slot locks. Pipes, machines and "give" helpers go through each loader's
 * transfer API, so the insert itself comes from the loader's test class; what is asked of it and
 * what counts as a pass is the same on both.
 */
public final class SlotLockTransferScenarios {

    /** Inserts {@code count} of {@code item} into one player inventory slot; returns what went in. */
    @FunctionalInterface
    public interface PlayerSlotInsert {
        long insert(Player player, int slot, Item item, int count);
    }

    /** Inserts into a container from one side, letting it pick the slot; returns what went in. */
    @FunctionalInterface
    public interface SidedInsert {
        long insert(Container container, Direction side, Item item, int count);
    }

    private SlotLockTransferScenarios() {}

    public static void modInsertIntoPlayerSkipsLockedSlot(GameTestHelper helper, PlayerSlotInsert insert) {
        SlotLockTests.run(helper, player -> {
            SlotLockTests.lockPlayerSlots(SlotLockMode.HIDE, 5);
            long inserted = insert.insert(player, 5, Items.STONE, 3);
            if (inserted != 0 || !player.getInventory().getItem(5).isEmpty()) {
                helper.fail("a mod-style insert put stone into locked slot 5");
                return;
            }
            helper.succeed();
        });
    }

    public static void sidedModInsertSkipsLockedFurnaceSlot(GameTestHelper helper, SidedInsert insert) {
        SlotLockTests.run(helper, player -> {
            helper.setBlock(SlotLockTests.BLOCK, Blocks.FURNACE.defaultBlockState());
            var furnace = (Container) helper.getBlockEntity(SlotLockTests.BLOCK, net.minecraft.world.level.block.entity.BlockEntity.class);
            SlotLockTests.lockContainerSlots("minecraft:furnace", 0);

            // From above a furnace only takes into its input, slot 0.
            long inserted = insert.insert(furnace, Direction.UP, Items.RAW_IRON, 4);
            if (inserted != 0 || !furnace.getItem(0).isEmpty()) {
                helper.fail("a pipe put raw iron into the locked furnace input from above");
                return;
            }
            helper.succeed();
        });
    }
}
