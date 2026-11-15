package net.bananemdnsa.historystages.gametest;

import java.util.List;

import com.google.gson.Gson;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Item entries with an NBT/component criterion, written the way a 6.1.x stage file has them.
 *
 * <p>A criterion that no longer matches does not fail loudly: the entry just stops locking. So
 * every case here checks that the matching stack is locked, and the 1.21.1 shapes are the point.
 */
public final class NbtCriteriaLockTests {

    private NbtCriteriaLockTests() {}

    /** Parses the items list through the stage's own Gson path, like a stage file on disk. */
    private static void itemStage(String name, String itemsJson) {
        StageEntry parsed = new Gson().fromJson("{\"items\":" + itemsJson + "}", StageEntry.class);
        List<ItemEntry> entries = parsed.getItemEntries();
        GameTestStages.global(name, stage -> stage.setItemEntries(entries));
    }

    private static ItemStack sharpSword(GameTestHelper helper, int level) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SHARPNESS), level);
        return sword;
    }

    private static ItemStack named(String name) {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static void expectLocked(GameTestHelper helper, ItemStack stack, String what) {
        if (!StageLockHelper.isItemLockedForServer(stack)) {
            helper.fail(what);
            return;
        }
        helper.succeed();
    }

    @HsGameTest
    public static void theEditorsCustomNamePresetLocksTheNamedItem(GameTestHelper helper) {
        try {
            itemStage("nbt_name", "[{\"id\":\"minecraft:diamond_sword\","
                    + "\"nbt\":{\"components\":{\"minecraft:custom_name\":\"Excalibur\"}}}]");
            expectLocked(helper, named("Excalibur"), "a sword named Excalibur is free under the custom_name preset");
        } finally {
            GameTestStages.removeAll();
        }
    }

    @HsGameTest
    public static void aJsonTextCustomNameFrom1211StillLocks(GameTestHelper helper) {
        try {
            itemStage("nbt_name_json", "[{\"id\":\"minecraft:diamond_sword\","
                    + "\"nbt\":{\"components\":{\"minecraft:custom_name\":\"{\\\"text\\\":\\\"Excalibur\\\"}\"}}}]");
            expectLocked(helper, named("Excalibur"), "a 1.21.1 JSON-text custom_name criterion no longer matches");
        } finally {
            GameTestStages.removeAll();
        }
    }

    @HsGameTest
    public static void theLegacyEnchantmentsPathLocks(GameTestHelper helper) {
        try {
            itemStage("nbt_legacy_ench", "[{\"id\":\"minecraft:diamond_sword\","
                    + "\"nbt\":{\"Enchantments\":[{\"id\":\"minecraft:sharpness\",\"lvl\":\"4-5\"}]}}]");
            expectLocked(helper, sharpSword(helper, 5), "a Sharpness V sword is free under the legacy Enchantments criterion");
        } finally {
            GameTestStages.removeAll();
        }
    }

    @HsGameTest
    public static void enchantmentsComponentInThe1211LevelsFormLocks(GameTestHelper helper) {
        try {
            itemStage("nbt_levels", "[{\"id\":\"minecraft:diamond_sword\","
                    + "\"nbt\":{\"components\":{\"minecraft:enchantments\":"
                    + "{\"levels\":{\"minecraft:sharpness\":5},\"show_in_tooltip\":true}}}}]");
            expectLocked(helper, sharpSword(helper, 5),
                    "a Sharpness V sword is free under components.minecraft:enchantments in the 1.21.1 levels form");
        } finally {
            GameTestStages.removeAll();
        }
    }

    @HsGameTest
    public static void aNonMatchingLevelStaysFree(GameTestHelper helper) {
        try {
            itemStage("nbt_levels_miss", "[{\"id\":\"minecraft:diamond_sword\","
                    + "\"nbt\":{\"components\":{\"minecraft:enchantments\":{\"levels\":{\"minecraft:sharpness\":5}}}}}]");
            if (StageLockHelper.isItemLockedForServer(sharpSword(helper, 3))) {
                helper.fail("a Sharpness III sword is locked by a criterion asking for Sharpness 5");
                return;
            }
            // Without the criterion the plain sword must not be caught either.
            if (StageLockHelper.isItemLockedForServer(new ItemStack(Items.DIAMOND_SWORD))) {
                helper.fail("an unenchanted sword is locked by an enchantment criterion");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }
}
