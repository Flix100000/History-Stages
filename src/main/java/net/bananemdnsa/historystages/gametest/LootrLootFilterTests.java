package net.bananemdnsa.historystages.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.compat.lootr.StageLootFilter;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import noobanidus.mods.lootr.common.api.LootrAPI;
import noobanidus.mods.lootr.common.api.data.LootFiller;
import noobanidus.mods.lootr.common.api.filter.ILootrFilter;

/**
 * The stage gate on Lootr loot as it is rolled — issue #120.
 *
 * <p>Asked of the filter directly rather than by right-clicking a Lootr pot in the test world.
 * Handing a pot its contents runs {@code performTrigger}, {@code awardStat} and
 * {@code performUpdate}, and a GameTest player has no connection to send any of that to; see
 * {@code GameTestPlayers}. What is under examination is which items survive a roll, and that is
 * the whole of what the filter decides.
 *
 * <p>{@link #theFilterIsRegisteredWithLootr} is the control the others depend on: they build a
 * filter of their own, so only that one proves the service file reaches Lootr at all.
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
    public static void theFilterIsRegisteredWithLootr(GameTestHelper helper) {
        String expected = new StageLootFilter().getName();

        for (ILootrFilter filter : LootrAPI.getFilters()) {
            if (expected.equals(filter.getName())) {
                helper.succeed();
                return;
            }
        }
        helper.fail("Lootr knows no filter called '" + expected + "', so the service file under "
                + "META-INF/services never reached it. Nothing else in this suite can tell, "
                + "because the other tests build the filter themselves");
    }

    @GameTest(template = "empty")
    public static void aGloballyLockedItemIsStrippedFromTheRoll(GameTestHelper helper) {
        try {
            lockGlobally("rolled_global", Items.NETHERITE_INGOT);
            ServerPlayer player = GameTestPlayers.create(helper);

            ObjectArrayList<ItemStack> loot = lootOf(Items.NETHERITE_INGOT);
            roll(helper, loot, player);

            if (contains(loot, Items.NETHERITE_INGOT)) {
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

            ObjectArrayList<ItemStack> loot = lootOf(Items.HEART_OF_THE_SEA);
            roll(helper, loot, player);

            if (contains(loot, Items.HEART_OF_THE_SEA)) {
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
        // The counter-case: without it a filter that empties every list passes the two above.
        // A locked stage is put up all the same, holding a different item, so the scan really
        // runs and answers no — an empty stage map could be short-circuited before it.
        try {
            lockGlobally("rolled_bystander", Items.NETHERITE_SCRAP);
            ServerPlayer player = GameTestPlayers.create(helper);

            ObjectArrayList<ItemStack> loot = lootOf(Items.NAUTILUS_SHELL);
            roll(helper, loot, player);

            if (!contains(loot, Items.NAUTILUS_SHELL)) {
                helper.fail("nautilus_shell is in no stage at all, but the filter took it out of "
                        + "the roll anyway");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void aRollWithoutLootrStateIsLeftAlone(GameTestHelper helper) {
        // Lootr's hook sits in LootTable.fill, so the filter also sees every vanilla chest the
        // world generates. A null filler state is the only thing separating those from a player's
        // personal copy, and getting it wrong would filter world generation against whichever
        // player Lootr served last.
        try {
            lockGlobally("rolled_foreign", Items.TRIDENT);

            ObjectArrayList<ItemStack> loot = lootOf(Items.TRIDENT);
            new StageLootFilter().mutate(loot, null, contextIn(helper), helper.getLevel().getRandom());

            if (!contains(loot, Items.TRIDENT)) {
                helper.fail("a roll that is not Lootr's — no filler state — was filtered anyway. "
                        + "Every vanilla chest in the world goes through here");
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

            ObjectArrayList<ItemStack> loot = lootOf(Items.ECHO_SHARD);
            roll(helper, loot, player);

            if (contains(loot, Items.ECHO_SHARD)) {
                helper.fail("echo_shard is locked and should not have survived the roll");
                return;
            }
            if (loot.size() != 1 || loot.get(0).isEmpty()) {
                helper.fail("useReplacements is on, so the locked item should have been swapped "
                        + "rather than dropped — the roll came back with " + loot.size()
                        + " stack(s)");
                return;
            }
            helper.succeed();
        } finally {
            Config.GAMEPLAY.useReplacements.set(wasOn);
            GameTestStages.removeAll();
        }
    }

    private static void roll(GameTestHelper helper, ObjectArrayList<ItemStack> loot, ServerPlayer player) {
        LootFiller.LootFillerState state =
                new LootFiller.LootFillerState(null, player, null, null, null, null, 0L);
        new StageLootFilter().mutate(loot, state, contextIn(helper), helper.getLevel().getRandom());
    }

    private static LootContext contextIn(GameTestHelper helper) {
        LootParams params = new LootParams.Builder(helper.getLevel())
                .create(LootContextParamSets.EMPTY);
        return new LootContext.Builder(params).create(Optional.empty());
    }

    private static ObjectArrayList<ItemStack> lootOf(Item... items) {
        ObjectArrayList<ItemStack> loot = new ObjectArrayList<>();
        for (Item item : items) {
            loot.add(new ItemStack(item));
        }
        return loot;
    }

    private static boolean contains(ObjectArrayList<ItemStack> loot, Item item) {
        for (ItemStack stack : loot) {
            if (stack.is(item)) return true;
        }
        return false;
    }

    private static void lockGlobally(String stageName, Item item) {
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        GameTestStages.global(stageName, stage ->
                stage.setItemEntries(new ArrayList<>(List.of(new ItemEntry(id)))));
    }
}
