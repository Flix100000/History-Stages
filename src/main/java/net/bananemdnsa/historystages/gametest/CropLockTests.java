package net.bananemdnsa.historystages.gametest;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockGrowFeatureEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The crop lock against a live server.
 *
 * <p>Growth is asked through the same NeoForge hooks the blocks call ({@link CommonHooks#canCropGrow},
 * the bonemeal and grow-feature events) instead of random-ticking and waiting: real growth also
 * depends on light and a dice roll, and a test that waits for luck says nothing about the lock.
 */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CropLockTests {

    private CropLockTests() {}

    // ---------------------------------------------------------------------------------------
    // Helpers. Above the tests on purpose: GameTestCleanupGuardTest slices the file per test.
    // ---------------------------------------------------------------------------------------

    private static final BlockPos WHERE = new BlockPos(1, 2, 1);

    private static void cropStage(String name, List<String> crops) {
        cropStage(name, crops, stage -> {});
    }

    private static void cropStage(String name, List<String> crops, Consumer<net.bananemdnsa.historystages.data.StageEntry> more) {
        GameTestStages.global(name, stage -> {
            stage.setCrops(new ArrayList<>(crops));
            more.accept(stage);
        });
    }

    private static BlockState place(GameTestHelper helper, BlockState state) {
        helper.setBlock(WHERE.below(), Blocks.FARMLAND.defaultBlockState());
        helper.setBlock(WHERE, state);
        return helper.getBlockState(WHERE);
    }

    private static boolean mayGrow(GameTestHelper helper, BlockState state) {
        return CommonHooks.canCropGrow(helper.getLevel(), helper.absolutePos(WHERE), state, true);
    }

    // ---------------------------------------------------------------------------------------

    @GameTest(template = "empty")
    public static void gatedWheatDoesNotGrow(GameTestHelper helper) {
        try {
            cropStage("crop_wheat", List.of("minecraft:wheat"));
            BlockState wheat = place(helper, Blocks.WHEAT.defaultBlockState());
            if (mayGrow(helper, wheat)) {
                helper.fail("wheat is gated by a locked stage but the growth hook lets it grow");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void tagGatesWheat(GameTestHelper helper) {
        try {
            cropStage("crop_tag", List.of("#minecraft:crops"));
            BlockState wheat = place(helper, Blocks.WHEAT.defaultBlockState());
            if (mayGrow(helper, wheat)) {
                helper.fail("#minecraft:crops is gated but wheat, which carries that tag, still grows");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void ungatedCarrotGrows(GameTestHelper helper) {
        try {
            cropStage("crop_only_wheat", List.of("minecraft:wheat"));
            BlockState carrots = place(helper, Blocks.CARROTS.defaultBlockState());
            if (!mayGrow(helper, carrots)) {
                helper.fail("only wheat is gated, but carrots are kept from growing too");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void cropGrowsAfterUnlock(GameTestHelper helper) {
        StageData data = StageData.get(helper.getLevel());
        try {
            cropStage("crop_unlock", List.of("minecraft:wheat"));
            BlockState wheat = place(helper, Blocks.WHEAT.defaultBlockState());
            if (mayGrow(helper, wheat)) {
                helper.fail("wheat grows before its stage is unlocked");
                return;
            }
            data.addStage(GameTestStages.PREFIX + "crop_unlock");
            if (!mayGrow(helper, wheat)) {
                helper.fail("the stage is unlocked but wheat is still kept from growing");
                return;
            }
            helper.succeed();
        } finally {
            data.removeStage(GameTestStages.PREFIX + "crop_unlock");
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void cropGrowsWhenInterchangeablePartnerUnlocked(GameTestHelper helper) {
        StageData data = StageData.get(helper.getLevel());
        try {
            cropStage("crop_or_a", List.of("minecraft:wheat"), stage -> stage.setInterchangeable(true));
            cropStage("crop_or_b", List.of("minecraft:wheat"), stage -> stage.setInterchangeable(true));
            BlockState wheat = place(helper, Blocks.WHEAT.defaultBlockState());
            data.addStage(GameTestStages.PREFIX + "crop_or_a");
            if (!mayGrow(helper, wheat)) {
                helper.fail("one of two interchangeable stages is unlocked, wheat must grow");
                return;
            }
            helper.succeed();
        } finally {
            data.removeStage(GameTestStages.PREFIX + "crop_or_a");
            GameTestStages.removeAll();
        }
    }

    /** Dispenser path: no player. The bonemeal must be refused and stay in the stack. */
    @GameTest(template = "empty")
    public static void bonemealIsRefusedAndKept(GameTestHelper helper) {
        try {
            cropStage("crop_bonemeal", List.of("minecraft:wheat"));
            place(helper, Blocks.WHEAT.defaultBlockState());
            ItemStack bonemeal = new ItemStack(Items.BONE_MEAL, 1);

            BoneMealItem.applyBonemeal(bonemeal, helper.getLevel(), helper.absolutePos(WHERE), null);

            if (bonemeal.getCount() != 1) {
                helper.fail("bonemeal on a gated plant was used up");
                return;
            }
            int age = helper.getBlockState(WHERE).getValue(CropBlock.AGE);
            if (age != 0) {
                helper.fail("bonemeal made gated wheat grow to age " + age);
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void gatedSaplingDoesNotBecomeTree(GameTestHelper helper) {
        try {
            cropStage("crop_sapling", List.of("#minecraft:saplings"));
            helper.setBlock(WHERE.below(), Blocks.DIRT.defaultBlockState());
            helper.setBlock(WHERE, Blocks.OAK_SAPLING.defaultBlockState());

            BlockGrowFeatureEvent event = NeoForge.EVENT_BUS.post(new BlockGrowFeatureEvent(
                    helper.getLevel(), helper.getLevel().getRandom(), helper.absolutePos(WHERE), null));

            if (!event.isCanceled()) {
                helper.fail("a gated sapling was allowed to grow into its tree");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }
}
