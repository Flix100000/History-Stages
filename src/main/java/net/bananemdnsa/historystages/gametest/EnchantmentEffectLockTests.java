package net.bananemdnsa.historystages.gametest;

import java.util.List;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.LevelledLockEntry;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.NamedLockEntry;
import net.bananemdnsa.historystages.data.lock.category.BuiltInLockMatching;
import net.bananemdnsa.historystages.data.lock.engine.LockSubjects;
import net.bananemdnsa.historystages.data.lock.engine.StackContents;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Enchantment and effect locks, answered against live stacks.
 *
 * <p>The claim is the same as the fluid one: a stack nobody listed by id — a book, a sword, a
 * splash potion — comes back locked because of what it carries.
 */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EnchantmentEffectLockTests {

    private static final String SHARPNESS = "minecraft:sharpness";
    private static final String SPEED = "minecraft:speed";

    private EnchantmentEffectLockTests() {}

    private static void enchantmentStage(String name, LevelledLockEntry entry) {
        GameTestStages.global(name, stage -> stage.setEnchantmentEntries(List.of(entry)));
    }

    private static void effectStage(String name, LevelledLockEntry entry) {
        GameTestStages.global(name, stage -> stage.setEffectEntries(List.of(entry)));
    }

    private static Holder<Enchantment> enchantment(GameTestHelper helper, ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key);
    }

    private static ItemStack book(GameTestHelper helper, ResourceKey<Enchantment> key, int level) {
        return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment(helper, key), level));
    }

    private static ItemStack potion(net.minecraft.world.item.Item item, Holder<Potion> potion) {
        return PotionContents.createItemStack(item, potion);
    }

    @GameTest(template = "empty")
    public static void aBookWithALockedEnchantmentIsLocked(GameTestHelper helper) {
        try {
            enchantmentStage("ench_book", new LevelledLockEntry(SHARPNESS, 4, true));
            if (!StageLockHelper.isItemLockedForServer(book(helper, Enchantments.SHARPNESS, 5))) {
                helper.fail("a Sharpness V book is free although Sharpness 4+ is gated");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void aLowerLevelStaysFree(GameTestHelper helper) {
        try {
            enchantmentStage("ench_low", new LevelledLockEntry(SHARPNESS, 4, true));
            if (StageLockHelper.isItemLockedForServer(book(helper, Enchantments.SHARPNESS, 3))) {
                helper.fail("a Sharpness III book is locked although only 4+ is gated");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void aBookWithTwoEnchantmentsIsLockedByEitherOne(GameTestHelper helper) {
        try {
            enchantmentStage("ench_two", new LevelledLockEntry(SHARPNESS, 4, true));
            ItemStack book = book(helper, Enchantments.UNBREAKING, 1);
            book.update(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS,
                    net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY, enchantments -> {
                        var mutable = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(enchantments);
                        mutable.set(enchantment(helper, Enchantments.SHARPNESS), 5);
                        return mutable.toImmutable();
                    });
            if (!StageLockHelper.isItemLockedForServer(book)) {
                helper.fail("a book with Unbreaking I and Sharpness V is free; the second enchantment was not read");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void gearCarryingALockedEnchantmentIsLocked(GameTestHelper helper) {
        try {
            enchantmentStage("ench_gear", new LevelledLockEntry(SHARPNESS, 4, true));
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.enchant(enchantment(helper, Enchantments.SHARPNESS), 5);
            if (!StageLockHelper.isItemLockedForServer(sword)) {
                helper.fail("a Sharpness V sword is free although the entry locks items");
                return;
            }
            if (!StageLockHelper.isActionLockedForServer(sword, "attack")) {
                helper.fail("a Sharpness V sword reports \"attack\" as allowed - the action path does not read enchantments");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void lockItemsOffLeavesGearFree(GameTestHelper helper) {
        try {
            enchantmentStage("ench_gear_off", new LevelledLockEntry(SHARPNESS, 4, false));
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.enchant(enchantment(helper, Enchantments.SHARPNESS), 5);
            if (StageLockHelper.isItemLockedForServer(sword)) {
                helper.fail("lock_items is off, but the sword carrying the enchantment is locked");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void aPotionWithALockedEffectIsLocked(GameTestHelper helper) {
        try {
            effectStage("eff_potion", new LevelledLockEntry(SPEED));
            for (var item : List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION, Items.TIPPED_ARROW)) {
                if (!StageLockHelper.isItemLockedForServer(potion(item, Potions.SWIFTNESS))) {
                    helper.fail(item + " of Swiftness is free although Speed is gated");
                    return;
                }
                if (!StageLockHelper.isActionLockedForServer(potion(item, Potions.SWIFTNESS), "use")) {
                    helper.fail(item + " of Swiftness reports \"use\" as allowed - the action path does not read effects");
                    return;
                }
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void effectLevelThreshold(GameTestHelper helper) {
        try {
            effectStage("eff_level", new LevelledLockEntry(SPEED, 2, true));
            if (StageLockHelper.isItemLockedForServer(potion(Items.POTION, Potions.SWIFTNESS))) {
                helper.fail("Swiftness (Speed I) is locked although only Speed 2+ is gated");
                return;
            }
            if (!StageLockHelper.isItemLockedForServer(potion(Items.POTION, Potions.STRONG_SWIFTNESS))) {
                helper.fail("Swiftness II is free although Speed 2+ is gated");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /**
     * Every potion has the same item id. Answering a plain one first must not leave a "free"
     * verdict behind for the next potion to pick up.
     */
    @GameTest(template = "empty")
    public static void aPlainPotionAskedFirstDoesNotUnlockTheNext(GameTestHelper helper) {
        try {
            effectStage("eff_memo", new LevelledLockEntry(SPEED));
            if (StageLockHelper.isItemLockedForServer(potion(Items.POTION, Potions.WATER))) {
                helper.fail("a water bottle is locked by a Speed gate");
                return;
            }
            if (!StageLockHelper.isItemLockedForServer(potion(Items.POTION, Potions.SWIFTNESS))) {
                helper.fail("Swiftness is free after a water bottle was asked first - the per-id memo served the bottle's answer");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /** Books and potions belong to the mod of what they carry; gear does not. */
    @GameTest(template = "empty")
    public static void modLockCoversBooksAndPotionsButNotGear(GameTestHelper helper) {
        try {
            StageEntry stage = new StageEntry();
            stage.setModEntries(List.of(new NamedLockEntry("foo")));
            NamedLockEntry mod = stage.getModEntries().get(0);
            List<StackContents.Levelled> fooEnchant = List.of(new StackContents.Levelled("foo:zap", 1));

            LockSubjects.ItemSubject book = new LockSubjects.ItemSubject("minecraft:enchanted_book",
                    "minecraft", null, null, null,
                    new StackContents(List.of(), fooEnchant, List.of(), List.of()));
            LockSubjects.ItemSubject potion = new LockSubjects.ItemSubject("minecraft:potion",
                    "minecraft", null, null, null,
                    new StackContents(List.of(), List.of(), List.of(), List.of("foo:brew")));
            LockSubjects.ItemSubject sword = new LockSubjects.ItemSubject("minecraft:diamond_sword",
                    "minecraft", null, null, null,
                    new StackContents(fooEnchant, List.of(), List.of(), List.of()));

            if (!BuiltInLockMatching.modEntryMatches(mod, stage, book)) {
                helper.fail("a book storing foo:zap is not covered by the foo mod lock");
                return;
            }
            if (!BuiltInLockMatching.modEntryMatches(mod, stage, potion)) {
                helper.fail("a potion of foo:brew is not covered by the foo mod lock");
                return;
            }
            if (BuiltInLockMatching.modEntryMatches(mod, stage, sword)) {
                helper.fail("a vanilla sword carrying foo:zap is covered by the foo mod lock");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }
}
