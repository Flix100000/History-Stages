package net.bananemdnsa.historystages.gametest;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.lock.SlotLockEntry;
import net.bananemdnsa.historystages.data.lock.SlotLockMode;
import net.bananemdnsa.historystages.util.lock.LockFeedback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Slot locks against a live server, through the same menu and packet paths a real client uses.
 *
 * <p>Each refusal is paired with a check that the same action does go through on an open slot, so
 * a test cannot pass just because the action did nothing.
 */
public class SlotLockTests {

    public SlotLockTests() {}

    // ---------------------------------------------------------------------------------------
    // Helpers. Above the tests on purpose: GameTestCleanupGuardTest slices the file per test.
    // ---------------------------------------------------------------------------------------

    /** InventoryMenu lays out the hotbar at menu slots 36-44. */
    private static final int MENU_HOTBAR_START = 36;

    private static void lockPlayerSlots(SlotLockMode mode, Integer... slots) {
        GameTestStages.global("slot_lock", stage -> stage.setSlotLocks(new ArrayList<>(List.of(
                new SlotLockEntry(SlotLockEntry.PLAYER, List.of(slots), mode)))));
    }

    /** A connected survival player whose lock feedback is already on cooldown, so nothing is sent mid-test. */
    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.createConnected(helper);
        LockFeedback.checkCooldown(player.getUUID(), "slot", LockFeedback.DEFAULT_COOLDOWN_MS);
        return player;
    }

    private static void fillInventory(Inventory inventory, int... except) {
        outer:
        for (int i = 0; i < 36; i++) {
            for (int skip : except) if (skip == i) continue outer;
            inventory.setItem(i, new ItemStack(Items.DIRT, 64));
        }
    }

    /** A second stage beside {@link #lockPlayerSlots}, for tests that need two modes at once. */
    private static void lockPlayerSlotsIn(String stageName, SlotLockMode mode, Integer... slots) {
        GameTestStages.global(stageName, stage -> stage.setSlotLocks(new ArrayList<>(List.of(
                new SlotLockEntry(SlotLockEntry.PLAYER, List.of(slots), mode)))));
    }

    private static void lockContainerSlots(String target, Integer... slots) {
        GameTestStages.global("slot_lock_container", stage -> stage.setSlotLocks(new ArrayList<>(List.of(
                new SlotLockEntry(target, List.of(slots), SlotLockMode.OVERLAY)))));
    }

    private static final BlockPos BLOCK = new BlockPos(1, 2, 1);

    /** A placed block entity's own menu, as the block would hand it to a player. */
    private static net.minecraft.world.inventory.AbstractContainerMenu menuOf(GameTestHelper helper, ServerPlayer player,
                                                                                net.minecraft.world.level.block.Block block) {
        helper.setBlock(BLOCK, block.defaultBlockState());
        var be = helper.getBlockEntity(BLOCK);
        return ((net.minecraft.world.MenuProvider) be).createMenu(1, player.getInventory(), player);
    }

    /** True when the menu let a click put stone into its slot 0. */
    private static boolean placesIntoFirstSlot(net.minecraft.world.inventory.AbstractContainerMenu menu, ServerPlayer player) {
        menu.setCarried(new ItemStack(Items.STONE, 1));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        return !menu.getSlot(0).getItem().isEmpty();
    }

    /** Individual stage locking player slot 3, granted to the player; returns its id. */
    private static String grantedSlotStage(ServerPlayer player, GameTestHelper helper) {
        GameTestStages.individual("slot_sweep", stage -> stage.setSlotLocks(new ArrayList<>(List.of(
                new SlotLockEntry(SlotLockEntry.PLAYER, List.of(3), SlotLockMode.HIDE)))));
        String id = GameTestStages.PREFIX + "slot_sweep";
        net.bananemdnsa.historystages.data.saveddata.IndividualStageData.get(helper.getLevel())
                .addStage(player.getUUID(), id);
        return id;
    }

    private static void run(GameTestHelper helper, Consumer<ServerPlayer> body) {
        try {
            body.accept(player(helper));
        } finally {
            GameTestStages.removeAll();
        }
    }

    // ---------------------------------------------------------------------------------------

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void lockedHotbarSlotRefusesPlacement(GameTestHelper helper) {
        run(helper, player -> {
            lockPlayerSlots(SlotLockMode.OVERLAY, 0);
            InventoryMenu menu = player.inventoryMenu;

            menu.setCarried(new ItemStack(Items.STONE, 4));
            menu.clicked(MENU_HOTBAR_START, 0, ClickType.PICKUP, player);
            if (!player.getInventory().getItem(0).isEmpty()) {
                helper.fail("hotbar slot 0 is locked but a click placed stone in it");
                return;
            }
            menu.clicked(MENU_HOTBAR_START + 1, 0, ClickType.PICKUP, player);
            if (player.getInventory().getItem(1).getCount() != 4) {
                helper.fail("hotbar slot 1 is open but the same click did not place the stone");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void itemInLockedSlotCannotBeTakenOut(GameTestHelper helper) {
        run(helper, player -> {
            player.getInventory().setItem(0, new ItemStack(Items.STONE, 4));
            lockPlayerSlots(SlotLockMode.OVERLAY, 0);

            player.inventoryMenu.clicked(MENU_HOTBAR_START, 0, ClickType.PICKUP, player);
            if (!player.inventoryMenu.getCarried().isEmpty()) {
                helper.fail("hotbar slot 0 is locked but its stone was picked up");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void pickupSkipsLockedSlots(GameTestHelper helper) {
        run(helper, player -> {
            fillInventory(player.getInventory(), 5);
            lockPlayerSlots(SlotLockMode.HIDE, 5);

            ItemStack stone = new ItemStack(Items.STONE, 3);
            if (player.getInventory().add(stone) || !player.getInventory().getItem(5).isEmpty()) {
                helper.fail("the only free slot is locked, yet the stone went into the inventory");
                return;
            }
            GameTestStages.removeAll();
            if (!player.getInventory().add(new ItemStack(Items.STONE, 3))) {
                helper.fail("with the lock gone, the free slot did not take the stone");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void shiftClickFromChestOnlyFillsOpenSlots(GameTestHelper helper) {
        run(helper, player -> {
            // Inventory slots 9 and 10 are free; quick-move fills from the far end, so 10 would
            // be chosen first if the lock did not stop it.
            fillInventory(player.getInventory(), 9, 10);
            lockPlayerSlots(SlotLockMode.OVERLAY, 10);
            ChestMenu menu = ChestMenu.threeRows(1, player.getInventory(), new SimpleContainer(27));
            menu.getSlot(0).set(new ItemStack(Items.STONE, 64));

            menu.clicked(0, 0, ClickType.QUICK_MOVE, player);

            if (!player.getInventory().getItem(10).isEmpty()) {
                helper.fail("shift-click put stone into locked inventory slot 10");
                return;
            }
            if (player.getInventory().getItem(9).getCount() != 64) {
                helper.fail("shift-click did not put the stone into open slot 9");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void numberKeySwapIntoLockedHotbarSlotRefused(GameTestHelper helper) {
        run(helper, player -> {
            lockPlayerSlots(SlotLockMode.OVERLAY, 2);
            ChestMenu menu = ChestMenu.threeRows(1, player.getInventory(), new SimpleContainer(27));
            menu.getSlot(0).set(new ItemStack(Items.STONE, 8));

            menu.clicked(0, 2, ClickType.SWAP, player);
            if (!player.getInventory().getItem(2).isEmpty() || menu.getSlot(0).getItem().getCount() != 8) {
                helper.fail("number key 3 swapped stone into locked hotbar slot 2");
                return;
            }
            menu.clicked(0, 3, ClickType.SWAP, player);
            if (player.getInventory().getItem(3).getCount() != 8) {
                helper.fail("number key 4 on an open hotbar slot did not swap");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void offhandSwapRefusedWhenOffhandLocked(GameTestHelper helper) {
        run(helper, player -> {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 2));
            lockPlayerSlots(SlotLockMode.HIDE, 40);

            player.connection.handlePlayerAction(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));

            if (!player.getOffhandItem().isEmpty() || player.getMainHandItem().getCount() != 2) {
                helper.fail("the offhand is locked but F moved the stone into it");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void selectingLockedHotbarSlotIsIgnored(GameTestHelper helper) {
        run(helper, player -> {
            player.getInventory().selected = 0;
            lockPlayerSlots(SlotLockMode.HIDE, 3);

            player.connection.handleSetCarriedItem(new ServerboundSetCarriedItemPacket(3));
            if (player.getInventory().selected != 0) {
                helper.fail("hotbar slot 3 is locked but the server selected it");
                return;
            }
            player.connection.handleSetCarriedItem(new ServerboundSetCarriedItemPacket(4));
            if (player.getInventory().selected != 4) {
                helper.fail("selecting open hotbar slot 4 was refused");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void creativePlayerIgnoresSlotLocks(GameTestHelper helper) {
        run(helper, player -> {
            player.setGameMode(GameType.CREATIVE);
            lockPlayerSlots(SlotLockMode.HIDE, 0);

            player.inventoryMenu.setCarried(new ItemStack(Items.STONE, 1));
            player.inventoryMenu.clicked(MENU_HOTBAR_START, 0, ClickType.PICKUP, player);
            if (player.getInventory().getItem(0).isEmpty()) {
                helper.fail("a creative player was kept out of a locked slot");
                return;
            }
            helper.succeed();
        });
    }

    // ---- containers ------------------------------------------------------------------

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void barrelLockDoesNotTouchChest(GameTestHelper helper) {
        run(helper, player -> {
            lockContainerSlots("minecraft:barrel", 0);
            if (placesIntoFirstSlot(menuOf(helper, player, net.minecraft.world.level.block.Blocks.BARREL), player)) {
                helper.fail("barrel slot 0 is locked but a click placed stone in it");
                return;
            }
            if (!placesIntoFirstSlot(menuOf(helper, player, net.minecraft.world.level.block.Blocks.CHEST), player)) {
                helper.fail("only barrels are locked, yet the chest refused its slot 0");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void menuWithoutBlockUsesMenuType(GameTestHelper helper) {
        run(helper, player -> {
            lockContainerSlots("minecraft:generic_9x3", 0);
            ChestMenu menu = ChestMenu.threeRows(1, player.getInventory(), new SimpleContainer(27));
            if (placesIntoFirstSlot(menu, player)) {
                helper.fail("generic_9x3 slot 0 is locked but a block-less chest menu accepted stone");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void hopperRespectsGlobalBarrelLock(GameTestHelper helper) {
        run(helper, player -> {
            helper.setBlock(BLOCK, net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState());
            var barrel = (net.minecraft.world.Container) helper.getBlockEntity(BLOCK);
            for (int i = 1; i < barrel.getContainerSize(); i++) barrel.setItem(i, new ItemStack(Items.DIRT, 64));
            lockContainerSlots("minecraft:barrel", 0);

            ItemStack rest = net.minecraft.world.level.block.entity.HopperBlockEntity.addItem(
                    null, barrel, new ItemStack(Items.STONE, 1), Direction.DOWN);
            if (rest.isEmpty() || !barrel.getItem(0).isEmpty()) {
                helper.fail("a hopper pushed stone into locked barrel slot 0");
                return;
            }
            GameTestStages.removeAll();
            rest = net.minecraft.world.level.block.entity.HopperBlockEntity.addItem(
                    null, barrel, new ItemStack(Items.STONE, 1), Direction.DOWN);
            if (!rest.isEmpty()) {
                helper.fail("with the lock gone the hopper still could not fill barrel slot 0");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void doubleChestHopperSkipsLockedHalf(GameTestHelper helper) {
        run(helper, player -> {
            var chestState = net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState();
            helper.setBlock(BLOCK, chestState.setValue(net.minecraft.world.level.block.ChestBlock.TYPE,
                    net.minecraft.world.level.block.state.properties.ChestType.LEFT));
            helper.setBlock(BLOCK.east(), chestState.setValue(net.minecraft.world.level.block.ChestBlock.TYPE,
                    net.minecraft.world.level.block.state.properties.ChestType.RIGHT));
            var state = helper.getBlockState(BLOCK);
            var compound = net.minecraft.world.level.block.ChestBlock.getContainer(
                    (net.minecraft.world.level.block.ChestBlock) state.getBlock(), state, helper.getLevel(),
                    helper.absolutePos(BLOCK), true);
            if (compound == null || compound.getContainerSize() != 54) {
                helper.fail("the test setup did not form a double chest");
                return;
            }
            Integer[] firstHalf = new Integer[27];
            for (int i = 0; i < 27; i++) firstHalf[i] = i;
            lockContainerSlots("minecraft:chest", firstHalf);

            net.minecraft.world.level.block.entity.HopperBlockEntity.addItem(
                    null, compound, new ItemStack(Items.STONE, 1), Direction.DOWN);
            for (int i = 0; i < 27; i++) {
                if (!compound.getItem(i).isEmpty()) {
                    helper.fail("a hopper put stone into locked double chest index " + i);
                    return;
                }
            }
            if (compound.getItem(27).isEmpty()) {
                helper.fail("the open half of the double chest did not take the hopper's stone");
                return;
            }
            helper.succeed();
        });
    }

    // ---- drop sweep --------------------------------------------------------------------

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void relockDropsItemFromLockedSlot(GameTestHelper helper) {
        run(helper, player -> {
            boolean original = net.bananemdnsa.historystages.Config.GAMEPLAY.dropItemsFromLockedSlots.get();
            try {
                net.bananemdnsa.historystages.Config.GAMEPLAY.dropItemsFromLockedSlots.set(true);
                String id = grantedSlotStage(player, helper);
                player.getInventory().setItem(3, new ItemStack(Items.STONE, 5));
                player.getInventory().setItem(4, new ItemStack(Items.STONE, 5));

                net.bananemdnsa.historystages.api.stage.StageStates.relockIndividual(id, player);

                if (!player.getInventory().getItem(3).isEmpty()) {
                    helper.fail("slot 3 became locked but its stone stayed in it");
                    return;
                }
                if (player.getInventory().getItem(4).getCount() != 5) {
                    helper.fail("the sweep touched open slot 4");
                    return;
                }
                helper.succeed();
            } finally {
                net.bananemdnsa.historystages.Config.GAMEPLAY.dropItemsFromLockedSlots.set(original);
            }
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void keepConfigLeavesItemFrozen(GameTestHelper helper) {
        run(helper, player -> {
            boolean original = net.bananemdnsa.historystages.Config.GAMEPLAY.dropItemsFromLockedSlots.get();
            try {
                net.bananemdnsa.historystages.Config.GAMEPLAY.dropItemsFromLockedSlots.set(false);
                String id = grantedSlotStage(player, helper);
                player.getInventory().setItem(3, new ItemStack(Items.STONE, 5));

                net.bananemdnsa.historystages.api.stage.StageStates.relockIndividual(id, player);

                if (player.getInventory().getItem(3).getCount() != 5) {
                    helper.fail("with the drop switch off, the stone left locked slot 3");
                    return;
                }
                player.inventoryMenu.clicked(MENU_HOTBAR_START + 3, 0, ClickType.PICKUP, player);
                if (!player.inventoryMenu.getCarried().isEmpty()) {
                    helper.fail("the kept stone could still be clicked out of locked slot 3");
                    return;
                }
                helper.succeed();
            } finally {
                net.bananemdnsa.historystages.Config.GAMEPLAY.dropItemsFromLockedSlots.set(original);
            }
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void containerSlotsNeverDrop(GameTestHelper helper) {
        run(helper, player -> {
            helper.setBlock(BLOCK, net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState());
            var barrel = (net.minecraft.world.Container) helper.getBlockEntity(BLOCK);
            barrel.setItem(0, new ItemStack(Items.STONE, 5));
            lockContainerSlots("minecraft:barrel", 0);

            net.bananemdnsa.historystages.util.lock.SlotLockSweeper.sweep(player);

            if (barrel.getItem(0).getCount() != 5) {
                helper.fail("the sweep emptied a locked barrel slot");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void creativeKeepsItemsInLockedSlots(GameTestHelper helper) {
        run(helper, player -> {
            player.setGameMode(GameType.CREATIVE);
            player.getInventory().setItem(3, new ItemStack(Items.STONE, 5));
            lockPlayerSlots(SlotLockMode.HIDE, 3);

            net.bananemdnsa.historystages.util.lock.SlotLockSweeper.sweep(player);

            if (player.getInventory().getItem(3).getCount() != 5) {
                helper.fail("a creative player's item was swept out of a locked slot");
                return;
            }
            helper.succeed();
        });
    }

    // ---- review fixes: real hoppers, mod inserts, pick-block, merges, mod slots, armor, preview ----

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void realHopperRespectsBarrelLock(GameTestHelper helper) {
        run(helper, player -> {
            helper.setBlock(BLOCK, net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState());
            var barrel = (net.minecraft.world.Container) helper.getBlockEntity(BLOCK);
            for (int i = 1; i < barrel.getContainerSize(); i++) barrel.setItem(i, new ItemStack(Items.DIRT, 64));
            helper.setBlock(BLOCK.above(), net.minecraft.world.level.block.Blocks.HOPPER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.HopperBlock.FACING, Direction.DOWN));
            var hopper = (net.minecraft.world.level.block.entity.HopperBlockEntity) helper.getBlockEntity(BLOCK.above());
            hopper.setItem(0, new ItemStack(Items.STONE, 1));
            lockContainerSlots("minecraft:barrel", 0);

            // The call the hopper's push ends in, without its cooldown.
            net.minecraft.world.level.block.entity.HopperBlockEntity.addItem(hopper, barrel,
                    hopper.removeItem(0, 1), Direction.DOWN);
            if (!barrel.getItem(0).isEmpty()) {
                helper.fail("a real hopper pushed stone into locked barrel slot 0");
                return;
            }
            GameTestStages.removeAll();
            net.minecraft.world.level.block.entity.HopperBlockEntity.addItem(hopper, barrel,
                    new ItemStack(Items.STONE, 1), Direction.DOWN);
            if (barrel.getItem(0).isEmpty()) {
                helper.fail("with the lock gone the real hopper still did not fill barrel slot 0");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void realHopperDoesNotDrainLockedSlot(GameTestHelper helper) {
        run(helper, player -> {
            helper.setBlock(BLOCK.above(), net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState());
            var barrel = (net.minecraft.world.Container) helper.getBlockEntity(BLOCK.above());
            barrel.setItem(0, new ItemStack(Items.STONE, 4));
            helper.setBlock(BLOCK, net.minecraft.world.level.block.Blocks.HOPPER.defaultBlockState());
            var hopper = (net.minecraft.world.level.block.entity.HopperBlockEntity) helper.getBlockEntity(BLOCK);
            lockContainerSlots("minecraft:barrel", 0);

            net.minecraft.world.level.block.entity.HopperBlockEntity.suckInItems(helper.getLevel(), hopper);
            if (barrel.getItem(0).getCount() != 4) {
                helper.fail("a real hopper pulled stone out of locked barrel slot 0");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void modInsertIntoPlayerSkipsLockedSlot(GameTestHelper helper) {
        run(helper, player -> {
            lockPlayerSlots(SlotLockMode.HIDE, 5);
            var storage = net.fabricmc.fabric.api.transfer.v1.item.PlayerInventoryStorage.of(player);
            long inserted;
            try (var tx = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
                inserted = storage.getSlot(5).insert(
                        net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(Items.STONE), 3, tx);
                tx.commit();
            }
            if (inserted != 0 || !player.getInventory().getItem(5).isEmpty()) {
                helper.fail("a mod-style insert put stone into locked slot 5");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void pickBlockAvoidsLockedHotbarSlots(GameTestHelper helper) {
        run(helper, player -> {
            Inventory inventory = player.getInventory();
            // Slots 4-8 full and 5 selected: vanilla wraps round to empty slot 0 for the picked item.
            inventory.selected = 5;
            for (int i = 4; i <= 8; i++) inventory.setItem(i, new ItemStack(Items.DIRT, 1));
            inventory.setItem(20, new ItemStack(Items.STONE, 1));
            lockPlayerSlots(SlotLockMode.HIDE, 0, 1, 2, 3);

            inventory.pickSlot(20);
            for (int i = 0; i <= 3; i++) {
                if (!inventory.getItem(i).isEmpty() || inventory.selected == i) {
                    helper.fail("pick-block used locked hotbar slot " + i);
                    return;
                }
            }
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void pickBlockLeavesLockedMainSlot(GameTestHelper helper) {
        run(helper, player -> {
            Inventory inventory = player.getInventory();
            inventory.setItem(20, new ItemStack(Items.STONE, 1));
            lockPlayerSlots(SlotLockMode.OVERLAY, 20);

            inventory.pickSlot(20);
            if (inventory.getItem(20).isEmpty()) {
                helper.fail("pick-block pulled stone out of locked inventory slot 20");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void shiftClickDoesNotTopUpLockedChestSlot(GameTestHelper helper) {
        run(helper, player -> {
            lockContainerSlots("minecraft:generic_9x3", 0);
            ChestMenu menu = ChestMenu.threeRows(1, player.getInventory(), new SimpleContainer(27));
            menu.getSlot(0).set(new ItemStack(Items.COBBLESTONE, 32));
            player.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 16));

            // ChestMenu.threeRows: the player's main inventory starts at menu slot 27.
            menu.clicked(27, 0, ClickType.QUICK_MOVE, player);
            if (menu.getSlot(0).getItem().getCount() != 32) {
                helper.fail("shift-click topped up locked chest slot 0");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void modItemHandlerSlotRespectsLock(GameTestHelper helper) {
        run(helper, player -> {
            lockContainerSlots("hstest:machine", 0);
            // Fabric has no item-handler slot; a mod menu's plain slot over its own container.
            var handler = new SimpleContainer(1);
            var menu = new net.minecraft.world.inventory.AbstractContainerMenu(null, 1) {
                {
                    addSlot(new net.minecraft.world.inventory.Slot(handler, 0, 0, 0));
                }
                @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player p, int i) { return ItemStack.EMPTY; }
                @Override public boolean stillValid(net.minecraft.world.entity.player.Player p) { return true; }
            };
            ((net.bananemdnsa.historystages.util.lock.SlotLockMenu) menu).historystages$setSlotTarget("hstest:machine");

            if (menu.getSlot(0).mayPlace(new ItemStack(Items.STONE))) {
                helper.fail("a locked mod item-handler slot still accepts items");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void rightClickArmorRefusedForLockedSlot(GameTestHelper helper) {
        run(helper, player -> {
            lockPlayerSlots(SlotLockMode.HIDE, 39);
            ItemStack helmet = new ItemStack(Items.IRON_HELMET);
            player.setItemInHand(InteractionHand.MAIN_HAND, helmet);

            player.gameMode.useItem(player, helper.getLevel(), helmet, InteractionHand.MAIN_HAND);
            if (!player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()) {
                helper.fail("right-clicking a helmet put it into locked helmet slot 39");
                return;
            }
            helper.succeed();
        });
    }

    /** Dispensers pick who to dress through this selector; a player with that armor slot locked is skipped. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void dispenserSkipsPlayerWithLockedArmorSlot(GameTestHelper helper) {
        run(helper, player -> {
            ItemStack helmet = new ItemStack(Items.IRON_HELMET);
            var selector = new net.minecraft.world.entity.EntitySelector.MobCanWearArmorEntitySelector(helmet);
            if (!selector.test(player)) {
                helper.fail("without a lock a dispenser should be able to put a helmet on the player");
                return;
            }
            lockPlayerSlots(SlotLockMode.HIDE, 39);
            if (selector.test(player)) {
                helper.fail("helmet slot 39 is locked but a dispenser would still put a helmet on");
                return;
            }
            helper.succeed();
        });
    }

    // ---- addon api ----------------------------------------------------------------------

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void apiReportsPlayerSlotStates(GameTestHelper helper) {
        run(helper, player -> {
            lockPlayerSlots(SlotLockMode.HIDE, 3);
            lockPlayerSlotsIn("slot_api_overlay", SlotLockMode.OVERLAY, 5);

            if (net.bananemdnsa.historystages.api.lock.SlotLockQuery.state(player, 3)
                    != net.bananemdnsa.historystages.api.lock.SlotState.HIDDEN) {
                helper.fail("the api did not report hidden slot 3 as hidden");
                return;
            }
            if (net.bananemdnsa.historystages.api.lock.SlotLockQuery.state(player, 5)
                    != net.bananemdnsa.historystages.api.lock.SlotState.OVERLAY) {
                helper.fail("the api did not report overlay slot 5 as overlay");
                return;
            }
            if (net.bananemdnsa.historystages.api.lock.SlotLockQuery.isLocked(player, 4)) {
                helper.fail("the api called open slot 4 locked");
                return;
            }
            player.setGameMode(GameType.CREATIVE);
            if (net.bananemdnsa.historystages.api.lock.SlotLockQuery.isLocked(player, 3)) {
                helper.fail("the api called a slot locked for a creative player");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void apiReportsMenuAndContainerSlots(GameTestHelper helper) {
        run(helper, player -> {
            lockContainerSlots("minecraft:barrel", 0);
            var menu = menuOf(helper, player, net.minecraft.world.level.block.Blocks.BARREL);

            if (!"minecraft:barrel".equals(
                    net.bananemdnsa.historystages.api.lock.SlotLockQuery.targetOf(menu).orElse(null))) {
                helper.fail("the api did not name the barrel as the menu's target");
                return;
            }
            if (!net.bananemdnsa.historystages.api.lock.SlotLockQuery.isLocked(player, menu.getSlot(0))) {
                helper.fail("the api called locked barrel slot 0 open");
                return;
            }
            if (net.bananemdnsa.historystages.api.lock.SlotLockQuery.isLocked(player, menu.getSlot(1))) {
                helper.fail("the api called open barrel slot 1 locked");
                return;
            }
            if (net.bananemdnsa.historystages.api.lock.SlotLockQuery.containerState("minecraft:barrel", 0)
                    != net.bananemdnsa.historystages.api.lock.SlotState.OVERLAY) {
                helper.fail("the player-less container query missed barrel slot 0");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void chestLikePreviewMenusBuildWithoutALevel(GameTestHelper helper) {
        run(helper, player -> {
            for (var block : List.of(net.minecraft.world.level.block.Blocks.CHEST,
                    net.minecraft.world.level.block.Blocks.BARREL,
                    net.minecraft.world.level.block.Blocks.SHULKER_BOX,
                    net.minecraft.world.level.block.Blocks.FURNACE)) {
                try {
                    if (net.bananemdnsa.historystages.util.lock.SlotTargets.previewMenu(block, player) == null) {
                        helper.fail("no preview menu for " + block);
                        return;
                    }
                } catch (RuntimeException e) {
                    helper.fail("building the preview menu for " + block + " threw " + e);
                    return;
                }
            }
            helper.succeed();
        });
    }

    // ---- Fabric: the events and wrappers NeoForge provides itself ----

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void swapHandsRefusedForLockedOffhand(GameTestHelper helper) {
        run(helper, player -> {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
            lockPlayerSlots(SlotLockMode.HIDE, 40);

            player.connection.handlePlayerAction(new net.minecraft.network.protocol.game.ServerboundPlayerActionPacket(
                    net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                    BlockPos.ZERO, Direction.DOWN));
            if (!player.getOffhandItem().isEmpty()) {
                helper.fail("F put stone into the locked offhand slot");
                return;
            }
            GameTestStages.removeAll();
            player.connection.handlePlayerAction(new net.minecraft.network.protocol.game.ServerboundPlayerActionPacket(
                    net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                    BlockPos.ZERO, Direction.DOWN));
            if (player.getOffhandItem().isEmpty()) {
                helper.fail("with the lock gone F still did not swap the hands");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void openingAContainerSendsItsSlotTarget(GameTestHelper helper) {
        try {
            lockContainerSlots("minecraft:barrel", 0);
            helper.setBlock(BLOCK, net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState());
            ServerPlayer player = GameTestPlayers.create(helper);
            List<net.minecraft.network.protocol.Packet<?>> sent = new ArrayList<>();
            GameTestPlayers.connectCapturing(player, sent);

            player.openMenu((net.minecraft.world.MenuProvider) helper.getBlockEntity(BLOCK));
            boolean told = sent.stream().anyMatch(packet ->
                    packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom
                            && custom.payload() instanceof net.bananemdnsa.historystages.network.clientbound.SlotTargetPacket);
            if (!told) {
                helper.fail("opening a barrel with a slot lock did not tell the client which block the menu "
                        + "belongs to, so the container-open event never fired");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void sidedModInsertSkipsLockedFurnaceSlot(GameTestHelper helper) {
        run(helper, player -> {
            helper.setBlock(BLOCK, net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState());
            var furnace = (net.minecraft.world.Container) helper.getBlockEntity(BLOCK);
            lockContainerSlots("minecraft:furnace", 0);

            // From above a furnace only takes into its input, slot 0.
            var storage = net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage.of(furnace, Direction.UP);
            long inserted;
            try (var tx = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
                inserted = storage.insert(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(Items.RAW_IRON), 4, tx);
                tx.commit();
            }
            if (inserted != 0 || !furnace.getItem(0).isEmpty()) {
                helper.fail("a pipe put raw iron into the locked furnace input from above");
                return;
            }
            helper.succeed();
        });
    }
}
