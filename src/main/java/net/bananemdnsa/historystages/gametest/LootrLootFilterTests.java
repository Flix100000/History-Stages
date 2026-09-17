package net.bananemdnsa.historystages.gametest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.compat.lootr.StageLootFilter;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The stage gate on Lootr loot as it is rolled — issue #120.
 *
 * <p>Asked of the strip directly rather than by right-clicking a Lootr pot in the test world.
 * Handing a pot its contents runs {@code performTrigger}, {@code awardStat} and
 * {@code performUpdate}, and a GameTest player has no connection to send any of that to; see
 * {@code GameTestPlayers}. What is under examination is which items survive a roll, and that is
 * the whole of what the strip decides.
 *
 * <p>{@link #theHookReachedLootrsRoll} is the control the others depend on: they call the strip
 * themselves, so only that one proves the hook is on Lootr's class at all. Lootr's internals are
 * allowed to move between versions and the hook is not required, so without this test the gate
 * could vanish in silence.
 *
 * <p>Every test locks an item of its own. The suite ticks its tests side by side, and two tests
 * on one item would each be reading the other's stage. For the same reason the ones that expect a
 * locked item to disappear only check that it is gone, not what replaced it — {@code
 * useReplacements} is global, and {@link #aReplacementTakesThePlaceOfALockedItem} flips it while
 * they run.
 */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LootrLootFilterTests {

    private LootrLootFilterTests() {}

    @GameTest(template = "empty")
    public static void theHookReachedLootrsRoll(GameTestHelper helper) {
        Class<?> chestData;
        try {
            chestData = Class.forName("noobanidus.mods.lootr.data.ChestData");
        } catch (ClassNotFoundException missing) {
            helper.fail("Lootr no longer has noobanidus.mods.lootr.data.ChestData, so the loot "
                    + "gate has nowhere to hang. See mixin/lootr/ChestDataMixin");
            return;
        }

        for (Method method : chestData.getDeclaredMethods()) {
            if (method.getName().startsWith("historystages$")) {
                helper.succeed();
                return;
            }
        }
        helper.fail("none of the three hooks on ChestData.createInventory applied, so Lootr rolls "
                + "loot past the stage gate. Lootr reshaped the method the hook names");
    }

    @GameTest(template = "empty")
    public static void aGloballyLockedItemIsStrippedFromTheRoll(GameTestHelper helper) {
        try {
            lockGlobally("rolled_global", Items.NETHERITE_INGOT);
            ServerPlayer player = GameTestPlayers.create(helper);

            SimpleContainer rolled = lootOf(Items.NETHERITE_INGOT);
            StageLootFilter.strip(rolled, player);

            if (contains(rolled, Items.NETHERITE_INGOT)) {
                helper.fail("netherite_ingot sits in a locked global stage, but it survived the "
                        + "roll of a Lootr container");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void anIndividuallyLockedItemIsStrippedForThatPlayer(GameTestHelper helper) {
        try {
            GameTestStages.individual("rolled_individual", stage -> stage.setItemEntries(
                    new ArrayList<>(List.of(new ItemEntry("minecraft:heart_of_the_sea")))));
            ServerPlayer player = GameTestPlayers.create(helper);

            SimpleContainer rolled = lootOf(Items.HEART_OF_THE_SEA);
            StageLootFilter.strip(rolled, player);

            if (contains(rolled, Items.HEART_OF_THE_SEA)) {
                helper.fail("heart_of_the_sea sits in an individual stage this player has not "
                        + "unlocked, but it survived the roll. Lootr rolls one copy per player, "
                        + "so the individual half is exactly what this hook is for");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void anUnstagedItemSurvivesTheRoll(GameTestHelper helper) {
        // The counter-case: without it a strip that empties every container passes the two above.
        // A locked stage is put up all the same, holding a different item, so the scan really
        // runs and answers no — an empty stage map could be short-circuited before it.
        try {
            lockGlobally("rolled_bystander", Items.NETHERITE_SCRAP);
            ServerPlayer player = GameTestPlayers.create(helper);

            SimpleContainer rolled = lootOf(Items.NAUTILUS_SHELL);
            StageLootFilter.strip(rolled, player);

            if (!contains(rolled, Items.NAUTILUS_SHELL)) {
                helper.fail("nautilus_shell is in no stage at all, but the strip took it out of "
                        + "the roll anyway");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void aReplacementTakesThePlaceOfALockedItem(GameTestHelper helper) {
        boolean wasOn = Config.GAMEPLAY.useReplacements.get();
        try {
            Config.GAMEPLAY.useReplacements.set(true);
            lockGlobally("rolled_replaced", Items.ECHO_SHARD);
            ServerPlayer player = GameTestPlayers.create(helper);

            SimpleContainer rolled = lootOf(Items.ECHO_SHARD);
            StageLootFilter.strip(rolled, player);

            if (contains(rolled, Items.ECHO_SHARD)) {
                helper.fail("echo_shard is locked and should not have survived the roll");
                return;
            }
            if (rolled.getItem(0).isEmpty()) {
                helper.fail("useReplacements is on, so the locked item should have been swapped "
                        + "rather than dropped — the slot came back empty");
                return;
            }
            helper.succeed();
        } finally {
            Config.GAMEPLAY.useReplacements.set(wasOn);
            GameTestStages.removeAll();
        }
    }

    private static SimpleContainer lootOf(Item... items) {
        SimpleContainer rolled = new SimpleContainer(items.length);
        for (int i = 0; i < items.length; i++) {
            rolled.setItem(i, new ItemStack(items[i]));
        }
        return rolled;
    }

    private static boolean contains(SimpleContainer rolled, Item item) {
        for (int i = 0; i < rolled.getContainerSize(); i++) {
            if (rolled.getItem(i).is(item)) return true;
        }
        return false;
    }

    private static void lockGlobally(String stageName, Item item) {
        String id = ForgeRegistries.ITEMS.getKey(item).toString();
        GameTestStages.global(stageName, stage ->
                stage.setItemEntries(new ArrayList<>(List.of(new ItemEntry(id)))));
    }
}
