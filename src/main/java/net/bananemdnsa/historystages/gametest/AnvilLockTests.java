package net.bananemdnsa.historystages.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.util.lock.LockFeedback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Puts two items into an anvil and reads what it offers back, for a material an individual stage
 * locks against use.
 *
 * <p>The anvil had no test at all until Issue #103, where opening one crashed the client on 5.2.0
 * and 5.2.1: {@code AnvilUpdateMixin} casts the menu to {@code ItemCombinerMenuAccessor}, and that
 * accessor was missing from the mixin config, so the cast could not even load its class.
 * {@link net.bananemdnsa.historystages.mixin.MixinConfigGuardTest} keeps the config honest; this
 * walks the path that broke, because a config can list a mixin that still does the wrong thing.
 *
 * <p>Filling an input slot is what runs {@code createResult} — the slot writes to the input
 * container, the container reports the change, and the menu recomputes. That is the same chain
 * the crash report shows, so the items go in through the slots rather than the container.
 *
 * <p>The refusal has an allowed twin, as everywhere here: an empty result means nothing unless the
 * same two slots with an unstaged material do produce one.
 */
public class AnvilLockTests {

    private static final String LOCKED_MATERIAL = "minecraft:diamond";

    // ItemCombinerMenu: the two inputs first, then the result it offers.
    private static final int LEFT_SLOT = 0;
    private static final int RIGHT_SLOT = 1;
    private static final int RESULT_SLOT = 2;

    public AnvilLockTests() {}

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void repairingWithALockedMaterialOffersNothing(GameTestHelper helper) {
        atAnvil(helper, (menu, player) -> {
            menu.getSlot(LEFT_SLOT).set(damaged(Items.DIAMOND_SWORD));
            menu.getSlot(RIGHT_SLOT).set(new ItemStack(Items.DIAMOND));

            if (menu.getSlot(RESULT_SLOT).hasItem()) {
                helper.fail("a diamond locked against use still repaired a diamond sword");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void repairingWithAFreeMaterialGoesThrough(GameTestHelper helper) {
        atAnvil(helper, (menu, player) -> {
            menu.getSlot(LEFT_SLOT).set(damaged(Items.IRON_SWORD));
            menu.getSlot(RIGHT_SLOT).set(new ItemStack(Items.IRON_INGOT));

            if (!menu.getSlot(RESULT_SLOT).hasItem()) {
                helper.fail("iron is in no stage, but an anvil offered nothing for a damaged iron "
                        + "sword and an iron ingot");
                return;
            }
            helper.succeed();
        });
    }

    /** Worn enough that an anvil has something to mend. A pristine item repairs to nothing. */
    private static ItemStack damaged(net.minecraft.world.item.Item item) {
        ItemStack stack = new ItemStack(item);
        stack.setDamageValue(stack.getMaxDamage() / 2);
        return stack;
    }

    /**
     * Runs {@code body} against a fresh anvil window, with {@link #LOCKED_MATERIAL} in an
     * individual stage that locks nothing but use.
     */
    private static void atAnvil(GameTestHelper helper, BiConsumer<AnvilMenu, ServerPlayer> body) {
        if (!Config.GAMEPLAY.individualLockEnchanting.get()) {
            helper.fail("individual lockEnchanting is switched off in this run's config, "
                    + "so none of these tests would mean anything");
            return;
        }
        try {
            GameTestStages.individual("anvil_lock", stage -> stage.setItemEntries(new ArrayList<>(
                    List.of(new ItemEntry(LOCKED_MATERIAL, null, new ArrayList<>(List.of("use")))))));

            ServerPlayer player = GameTestPlayers.create(helper);
            // A refusal tells the player so on the actionbar, and this player has no connection.
            LockFeedback.checkCooldown(player.getUUID(), "enchant", LockFeedback.DEFAULT_COOLDOWN_MS);

            AnvilMenu menu = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
            body.accept(menu, player);
        } finally {
            GameTestStages.removeAll();
        }
    }
}
