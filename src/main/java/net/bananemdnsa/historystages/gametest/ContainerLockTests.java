package net.bananemdnsa.historystages.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.util.lock.LockFeedback;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Clicks in a chest window, sent through the menu the way the server handles a real click, for an
 * item an individual stage locks against pickup.
 *
 * <p>Every refusal here would also pass if the clicks did nothing at all, so each one has an
 * allowed twin that proves the same click does move items when the lock has no say in it.
 *
 * <p>The player has no connection, and a refused click tells the player so on the actionbar. The
 * feedback cooldown is used up before clicking, which keeps that packet from being sent at all.
 */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ContainerLockTests {

    private static final String LOCKED_ITEM = "minecraft:diamond";

    // ChestMenu.threeRows: chest slots first, then the main inventory, then the hotbar.
    private static final int CHEST_SLOT = 0;
    private static final int INVENTORY_SLOT = 27;
    private static final int OTHER_INVENTORY_SLOT = 28;

    private ContainerLockTests() {}

    @GameTest(template = "empty")
    public static void takingALockedItemOutOfAChestIsRefused(GameTestHelper helper) {
        inChest(helper, (menu, player) -> {
            menu.getSlot(CHEST_SLOT).set(new ItemStack(Items.DIAMOND, 5));

            menu.clicked(CHEST_SLOT, 0, ClickType.PICKUP, player);

            if (!menu.getCarried().isEmpty() || menu.getSlot(CHEST_SLOT).getItem().getCount() != 5) {
                helper.fail("a click on locked diamonds in a chest put them on the cursor");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void takingAFreeItemOutOfAChestGoesThrough(GameTestHelper helper) {
        inChest(helper, (menu, player) -> {
            menu.getSlot(CHEST_SLOT).set(new ItemStack(Items.DIRT, 5));

            menu.clicked(CHEST_SLOT, 0, ClickType.PICKUP, player);

            if (menu.getCarried().getCount() != 5 || menu.getSlot(CHEST_SLOT).hasItem()) {
                helper.fail("dirt is in no stage, but clicking it in a chest did not pick it up");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void shiftClickingALockedItemOutOfAChestIsRefused(GameTestHelper helper) {
        inChest(helper, (menu, player) -> {
            menu.getSlot(CHEST_SLOT).set(new ItemStack(Items.DIAMOND, 5));

            menu.clicked(CHEST_SLOT, 0, ClickType.QUICK_MOVE, player);

            if (player.getInventory().countItem(Items.DIAMOND) != 0) {
                helper.fail("shift-clicking locked diamonds moved them into the player's inventory");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void shiftClickingAFreeItemOutOfAChestGoesThrough(GameTestHelper helper) {
        inChest(helper, (menu, player) -> {
            menu.getSlot(CHEST_SLOT).set(new ItemStack(Items.DIRT, 5));

            menu.clicked(CHEST_SLOT, 0, ClickType.QUICK_MOVE, player);

            if (player.getInventory().countItem(Items.DIRT) != 5) {
                helper.fail("dirt is in no stage, but shift-clicking it did not move it out of the chest");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void aNumberKeyOverALockedItemInAChestIsRefused(GameTestHelper helper) {
        inChest(helper, (menu, player) -> {
            menu.getSlot(CHEST_SLOT).set(new ItemStack(Items.DIAMOND, 5));

            menu.clicked(CHEST_SLOT, 0, ClickType.SWAP, player);

            if (player.getInventory().countItem(Items.DIAMOND) != 0) {
                helper.fail("pressing a number key over locked diamonds swapped them into the hotbar");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void aNumberKeyOverAFreeItemInAChestGoesThrough(GameTestHelper helper) {
        inChest(helper, (menu, player) -> {
            menu.getSlot(CHEST_SLOT).set(new ItemStack(Items.DIRT, 5));

            menu.clicked(CHEST_SLOT, 0, ClickType.SWAP, player);

            if (player.getInventory().getItem(0).getCount() != 5) {
                helper.fail("dirt is in no stage, but a number key did not swap it into the hotbar");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void doubleClickingDoesNotGatherLockedItemsFromAChest(GameTestHelper helper) {
        inChest(helper, (menu, player) -> {
            menu.getSlot(CHEST_SLOT).set(new ItemStack(Items.DIAMOND, 5));
            menu.setCarried(new ItemStack(Items.DIAMOND, 1));

            // The double click lands on an empty inventory slot, which is exactly why looking at
            // the clicked slot never caught it.
            menu.clicked(INVENTORY_SLOT, 0, ClickType.PICKUP_ALL, player);

            if (menu.getCarried().getCount() != 1 || menu.getSlot(CHEST_SLOT).getItem().getCount() != 5) {
                helper.fail("a double click gathered locked diamonds out of the chest onto the cursor");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void doubleClickingStillGathersFreeItemsFromAChest(GameTestHelper helper) {
        inChest(helper, (menu, player) -> {
            menu.getSlot(CHEST_SLOT).set(new ItemStack(Items.DIRT, 5));
            menu.setCarried(new ItemStack(Items.DIRT, 1));

            menu.clicked(INVENTORY_SLOT, 0, ClickType.PICKUP_ALL, player);

            if (menu.getCarried().getCount() != 6) {
                helper.fail("dirt is in no stage, but a double click did not gather it from the chest");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void addingToAStackOfLockedItemsInAChestGoesThrough(GameTestHelper helper) {
        inChest(helper, (menu, player) -> {
            menu.getSlot(CHEST_SLOT).set(new ItemStack(Items.DIAMOND, 5));
            menu.setCarried(new ItemStack(Items.DIAMOND, 2));

            menu.clicked(CHEST_SLOT, 0, ClickType.PICKUP, player);

            if (!menu.getCarried().isEmpty() || menu.getSlot(CHEST_SLOT).getItem().getCount() != 7) {
                helper.fail("putting diamonds onto a stack of diamonds was refused, "
                        + "though it takes nothing out of the chest");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void sortingLockedItemsInsideTheOwnInventoryGoesThrough(GameTestHelper helper) {
        inChest(helper, (menu, player) -> {
            menu.getSlot(INVENTORY_SLOT).set(new ItemStack(Items.DIAMOND, 5));

            menu.clicked(INVENTORY_SLOT, 0, ClickType.PICKUP, player);
            menu.clicked(OTHER_INVENTORY_SLOT, 0, ClickType.PICKUP, player);

            if (menu.getSlot(OTHER_INVENTORY_SLOT).getItem().getCount() != 5) {
                helper.fail("moving locked diamonds from one inventory slot to another with the mouse "
                        + "was refused");
                return;
            }

            menu.clicked(OTHER_INVENTORY_SLOT, 0, ClickType.SWAP, player);

            if (player.getInventory().getItem(0).getCount() != 5) {
                helper.fail("moving locked diamonds into the hotbar with a number key was refused");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void throwingALockedItemAwayGoesThrough(GameTestHelper helper) {
        inChest(helper, (menu, player) -> {
            menu.getSlot(INVENTORY_SLOT).set(new ItemStack(Items.DIAMOND, 5));

            // Keeps the thrown stack out of the world; nothing here needs it on the ground.
            player.captureDrops(new ArrayList<>());
            try {
                menu.clicked(INVENTORY_SLOT, 1, ClickType.THROW, player);
            } finally {
                player.captureDrops(null);
            }

            if (menu.getSlot(INVENTORY_SLOT).hasItem()) {
                helper.fail("throwing locked diamonds away from the inventory was refused");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * Runs {@code body} against a fresh chest window, with {@link #LOCKED_ITEM} in an individual
     * stage that locks nothing but pickup.
     */
    private static void inChest(GameTestHelper helper, BiConsumer<ChestMenu, ServerPlayer> body) {
        if (!Config.GAMEPLAY.lockContainerInteraction.get()) {
            helper.fail("lockContainerInteraction is switched off in this run's config, "
                    + "so none of these tests would mean anything");
            return;
        }
        try {
            GameTestStages.individual("container_lock", stage -> stage.setItemEntries(new ArrayList<>(
                    List.of(new ItemEntry(LOCKED_ITEM, null, new ArrayList<>(List.of("pickup")))))));

            ServerPlayer player = GameTestPlayers.create(helper);
            LockFeedback.checkCooldown(player.getUUID(), "container", LockFeedback.DEFAULT_COOLDOWN_MS);

            ChestMenu menu = ChestMenu.threeRows(1, player.getInventory(), new SimpleContainer(27));
            body.accept(menu, player);
        } finally {
            GameTestStages.removeAll();
        }
    }
}
