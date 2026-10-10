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
            enchantmentStage("ench_book", new LevelledLockEntry(SHARPNESS, 4, null, null));
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
            enchantmentStage("ench_low", new LevelledLockEntry(SHARPNESS, 4, null, null));
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
            enchantmentStage("ench_two", new LevelledLockEntry(SHARPNESS, 4, null, null));
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
            enchantmentStage("ench_gear", new LevelledLockEntry(SHARPNESS, 4, null, null));
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
            enchantmentStage("ench_gear_off", new LevelledLockEntry(SHARPNESS, 4, List.of("enchanting_table", "anvil"), null));
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.enchant(enchantment(helper, Enchantments.SHARPNESS), 5);
            if (StageLockHelper.isItemLockedForServer(sword)) {
                helper.fail("the entry locks only the stations, but the sword carrying the enchantment is locked");
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
            effectStage("eff_level", new LevelledLockEntry(SPEED, 2, null, null));
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

    // --- stations ---------------------------------------------------------------------------

    private static net.minecraft.world.inventory.AnvilMenu anvil(net.minecraft.server.level.ServerPlayer player,
                                                                ItemStack left, ItemStack right) {
        var menu = new net.minecraft.world.inventory.AnvilMenu(1, player.getInventory(),
                net.minecraft.world.inventory.ContainerLevelAccess.NULL);
        menu.getSlot(0).set(left);
        menu.getSlot(1).set(right);
        menu.createResult();
        return menu;
    }

    @GameTest(template = "empty")
    public static void anvilRefusesALockedEnchantment(GameTestHelper helper) {
        try {
            // stations only, so neither input is locked and only the anvil can be the one refusing
            enchantmentStage("anvil_locked", new LevelledLockEntry(SHARPNESS, 4, List.of("enchanting_table", "anvil"), null));
            var player = GameTestPlayers.createConnected(helper);
            var menu = anvil(player, new ItemStack(Items.DIAMOND_SWORD), book(helper, Enchantments.SHARPNESS, 5));
            if (!menu.getSlot(2).getItem().isEmpty()) {
                helper.fail("the anvil produced a Sharpness V sword although Sharpness 4+ is gated");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void anvilAllowsAFreeLevel(GameTestHelper helper) {
        try {
            enchantmentStage("anvil_free", new LevelledLockEntry(SHARPNESS, 4, List.of("enchanting_table", "anvil"), null));
            var player = GameTestPlayers.createConnected(helper);
            var menu = anvil(player, new ItemStack(Items.DIAMOND_SWORD), book(helper, Enchantments.SHARPNESS, 3));
            if (menu.getSlot(2).getItem().isEmpty()) {
                helper.fail("the anvil refused Sharpness III although only 4+ is gated");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    private static boolean brewable(GameTestHelper helper, ItemStack ingredient, ItemStack potion)
            throws ReflectiveOperationException {
        var items = net.minecraft.core.NonNullList.withSize(5, ItemStack.EMPTY);
        items.set(0, potion);
        items.set(3, ingredient);
        var method = net.minecraft.world.level.block.entity.BrewingStandBlockEntity.class.getDeclaredMethod(
                "isBrewable", net.minecraft.world.item.alchemy.PotionBrewing.class, net.minecraft.core.NonNullList.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, helper.getLevel().potionBrewing(), items);
    }

    @GameTest(template = "empty")
    public static void brewingStandWillNotBrewALockedEffect(GameTestHelper helper) {
        try {
            effectStage("brew_locked", new LevelledLockEntry(SPEED));
            if (brewable(helper, new ItemStack(Items.SUGAR), potion(Items.POTION, Potions.AWKWARD))) {
                helper.fail("the brewing stand would brew Swiftness although Speed is gated globally");
                return;
            }
            if (!brewable(helper, new ItemStack(Items.GLISTERING_MELON_SLICE), potion(Items.POTION, Potions.AWKWARD))) {
                helper.fail("the brewing stand refused Healing although only Speed is gated");
                return;
            }
            helper.succeed();
        } catch (ReflectiveOperationException e) {
            helper.fail("could not ask the brewing stand: " + e);
        } finally {
            GameTestStages.removeAll();
        }
    }

    /** The stand brews with nobody standing at it, so only global stages can stop it. */
    @GameTest(template = "empty")
    public static void brewingStandIgnoresIndividualLocks(GameTestHelper helper) {
        try {
            GameTestStages.individual("brew_individual",
                    stage -> stage.setEffectEntries(List.of(new LevelledLockEntry(SPEED))));
            if (!brewable(helper, new ItemStack(Items.SUGAR), potion(Items.POTION, Potions.AWKWARD))) {
                helper.fail("an individual Speed lock stopped the brewing stand, which has no player to ask");
                return;
            }
            helper.succeed();
        } catch (ReflectiveOperationException e) {
            helper.fail("could not ask the brewing stand: " + e);
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void stationsRefuseEnchantmentsAndEffectsOfALockedMod(GameTestHelper helper) {
        try {
            GameTestStages.global("station_mod",
                    stage -> stage.setModEntries(List.of(new NamedLockEntry("foo"))));
            var player = GameTestPlayers.createConnected(helper);
            if (!StageLockHelper.isEnchantmentLockedForPlayer("foo:zap", 1, player.getUUID())) {
                helper.fail("an enchantment of a locked mod is allowed at the enchanting table");
                return;
            }
            if (!StageLockHelper.isEffectLockedForServer("foo:buzz", 1, "minecraft:potion")) {
                helper.fail("an effect of a locked mod is allowed at the brewing stand");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /** A mod entry narrowed away from making things must not start refusing stations. */
    @GameTest(template = "empty")
    public static void aModEntryWithoutRecipeLeavesStationsAlone(GameTestHelper helper) {
        try {
            GameTestStages.global("station_mod_narrow", stage -> stage.setModEntries(List.of(
                    new NamedLockEntry("foo", List.of("pickup")))));
            var player = GameTestPlayers.createConnected(helper);
            if (StageLockHelper.isEnchantmentLockedForPlayer("foo:zap", 1, player.getUUID())) {
                helper.fail("a mod entry that only locks pickup refuses that mod's enchantments");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void theEnchantingTableAsksTheNewEntries(GameTestHelper helper) {
        try {
            enchantmentStage("table", new LevelledLockEntry(SHARPNESS, 4, List.of("enchanting_table", "anvil"), null));
            var player = GameTestPlayers.createConnected(helper);
            if (!StageLockHelper.isEnchantmentLockedForPlayer(SHARPNESS, 4, player.getUUID())) {
                helper.fail("Sharpness IV is allowed at the table although 4+ is gated");
                return;
            }
            if (StageLockHelper.isEnchantmentLockedForPlayer(SHARPNESS, 3, player.getUUID())) {
                helper.fail("Sharpness III is refused at the table although only 4+ is gated");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /** The old way of locking an enchantment, a book entry with NBT, keeps doing what it did. */
    @GameTest(template = "empty")
    public static void anOldEnchantedBookEntryStillLocksTheTable(GameTestHelper helper) {
        try {
            com.google.gson.JsonObject nbt = com.google.gson.JsonParser.parseString(
                    "{\"StoredEnchantments\":[{\"id\":\"minecraft:sharpness\"}]}").getAsJsonObject();
            GameTestStages.global("old_book", stage -> stage.setItemEntries(new java.util.ArrayList<>(List.of(
                    new net.bananemdnsa.historystages.data.ItemEntry("minecraft:enchanted_book", nbt, null)))));
            var player = GameTestPlayers.createConnected(helper);
            if (!StageLockHelper.isEnchantmentLockedForPlayer(SHARPNESS, 2, player.getUUID())) {
                helper.fail("an old enchanted_book NBT entry no longer locks the enchanting table");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    // --- per-entry actions and item types --------------------------------------------------

    @GameTest(template = "empty")
    public static void aNarrowedEntryLocksOnlyItsActions(GameTestHelper helper) {
        try {
            enchantmentStage("act_pickup", new LevelledLockEntry(SHARPNESS, null, List.of("pickup"), null));
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.enchant(enchantment(helper, Enchantments.SHARPNESS), 5);
            if (!StageLockHelper.isActionLockedForServer(sword, "pickup")) {
                helper.fail("pickup is locked on the entry but a Sharpness sword may be picked up");
                return;
            }
            if (StageLockHelper.isActionLockedForServer(sword, "attack")) {
                helper.fail("only pickup is locked on the entry but attacking with the sword is refused");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void tableAndAnvilAreSeparateActions(GameTestHelper helper) {
        try {
            enchantmentStage("act_table", new LevelledLockEntry(SHARPNESS, null, List.of("enchanting_table"), null));
            var player = GameTestPlayers.createConnected(helper);
            if (!StageLockHelper.isEnchantmentLockedForPlayer(SHARPNESS, 3, "enchanting_table", player.getUUID())) {
                helper.fail("the entry locks the enchanting table but it is allowed");
                return;
            }
            var menu = anvil(player, new ItemStack(Items.DIAMOND_SWORD), book(helper, Enchantments.SHARPNESS, 3));
            if (menu.getSlot(2).getItem().isEmpty()) {
                helper.fail("the entry leaves the anvil alone but the anvil refused");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /** A stage that fixes "anvil" off overrides an entry that locks everything. */
    @GameTest(template = "empty")
    public static void fixedActionsReachTheStations(GameTestHelper helper) {
        try {
            GameTestStages.global("act_fixed", stage -> {
                stage.setEnchantmentEntries(List.of(new LevelledLockEntry(SHARPNESS)));
                stage.setFixedEnchantmentLockActions(java.util.Map.of("anvil", false));
            });
            var player = GameTestPlayers.createConnected(helper);
            if (StageLockHelper.isEnchantmentLockedForPlayer(SHARPNESS, 3, "anvil", player.getUUID())) {
                helper.fail("the stage fixes the anvil as allowed but the anvil is refused");
                return;
            }
            if (!StageLockHelper.isEnchantmentLockedForPlayer(SHARPNESS, 3, "enchanting_table", player.getUUID())) {
                helper.fail("only the anvil is fixed, the enchanting table must stay locked");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void aSparedItemTypeIsFree(GameTestHelper helper) {
        try {
            effectStage("types_arrow", new LevelledLockEntry(SPEED, null, null, List.of("minecraft:tipped_arrow")));
            if (StageLockHelper.isItemLockedForServer(potion(Items.TIPPED_ARROW, Potions.SWIFTNESS))
                    || StageLockHelper.isActionLockedForServer(potion(Items.TIPPED_ARROW, Potions.SWIFTNESS), "pickup")) {
                helper.fail("tipped arrows are spared but an arrow of Swiftness is locked");
                return;
            }
            if (!StageLockHelper.isItemLockedForServer(potion(Items.POTION, Potions.SWIFTNESS))) {
                helper.fail("only tipped arrows are spared but a potion of Swiftness is free");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void brewingFollowsActionsAndItemTypes(GameTestHelper helper) {
        try {
            effectStage("brew_types", new LevelledLockEntry(SPEED, null, null, List.of("minecraft:splash_potion")));
            if (brewable(helper, new ItemStack(Items.SUGAR), potion(Items.POTION, Potions.AWKWARD))) {
                helper.fail("a normal potion of Swiftness would be brewed although only splash potions are spared");
                return;
            }
            if (!brewable(helper, new ItemStack(Items.SUGAR), potion(Items.SPLASH_POTION, Potions.AWKWARD))) {
                helper.fail("splash potions are spared but a splash potion of Swiftness is not brewed");
                return;
            }
            GameTestStages.removeAll();
            effectStage("brew_off", new LevelledLockEntry(SPEED, null, List.of("use", "pickup"), null));
            if (!brewable(helper, new ItemStack(Items.SUGAR), potion(Items.POTION, Potions.AWKWARD))) {
                helper.fail("the entry does not lock brewing but the brewing stand refused");
                return;
            }
            helper.succeed();
        } catch (ReflectiveOperationException e) {
            helper.fail("could not ask the brewing stand: " + e);
        } finally {
            GameTestStages.removeAll();
        }
    }
}
