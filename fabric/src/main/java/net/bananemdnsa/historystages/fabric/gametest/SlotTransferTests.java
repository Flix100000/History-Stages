package net.bananemdnsa.historystages.fabric.gametest;

import net.bananemdnsa.historystages.gametest.SlotLockTransferScenarios;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.PlayerInventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.gametest.framework.GameTestHelper;

/** The slot lock against mod inserts through Fabric's transfer API. */
public class SlotTransferTests {

    @GameTest
    public void modInsertIntoPlayerSkipsLockedSlot(GameTestHelper helper) {
        SlotLockTransferScenarios.modInsertIntoPlayerSkipsLockedSlot(helper, (player, slot, item, count) -> {
            try (var tx = Transaction.openOuter()) {
                long inserted = PlayerInventoryStorage.of(player).getSlot(slot).insert(ItemVariant.of(item), count, tx);
                tx.commit();
                return inserted;
            }
        });
    }

    @GameTest
    public void sidedModInsertSkipsLockedFurnaceSlot(GameTestHelper helper) {
        SlotLockTransferScenarios.sidedModInsertSkipsLockedFurnaceSlot(helper, (container, side, item, count) -> {
            try (var tx = Transaction.openOuter()) {
                long inserted = ContainerStorage.of(container, side).insert(ItemVariant.of(item), count, tx);
                tx.commit();
                return inserted;
            }
        });
    }
}
