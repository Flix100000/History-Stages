package net.bananemdnsa.historystages.gametest;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.bananemdnsa.historystages.platform.PlantHooks;
import net.minecraft.world.level.block.SaplingBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The crop lock against a live server.
 *
 * <p>Growth is mostly asked through the hook the random-tick mixin calls ({@link PlantHooks#canCropGrow})
 * instead of random-ticking and waiting: real growth also depends on light and a dice roll, and a
 * test that waits for luck says nothing about the lock. Cactus is the exception: it grows on every
 * random tick, so the mixin itself can be held to it, with an unlocked twin.
 */
public class CropLockTests {

    public CropLockTests() {}

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
        return PlantHooks.canCropGrow(helper.getLevel(), helper.absolutePos(WHERE), state);
    }

    /** Cactus grows on every random tick with air above, so sixteen ticks are certain growth. */
    private static boolean cactusGrewAfterRandomTicks(GameTestHelper helper) {
        helper.setBlock(WHERE.below(), Blocks.SAND.defaultBlockState());
        helper.setBlock(WHERE, Blocks.CACTUS.defaultBlockState());
        BlockPos pos = helper.absolutePos(WHERE);
        for (int i = 0; i < 20; i++) {
            helper.getBlockState(WHERE).randomTick(helper.getLevel(), pos, helper.getLevel().getRandom());
        }
        return helper.getBlockState(WHERE.above()).is(Blocks.CACTUS);
    }

    // ---------------------------------------------------------------------------------------

    @HsGameTest
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

    @HsGameTest
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

    @HsGameTest
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

    @HsGameTest
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

    @HsGameTest
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
    @HsGameTest
    public static void bonemealIsRefusedAndKept(GameTestHelper helper) {
        try {
            cropStage("crop_bonemeal", List.of("minecraft:wheat"));
            place(helper, Blocks.WHEAT.defaultBlockState());
            ItemStack bonemeal = new ItemStack(Items.BONE_MEAL, 1);

            BoneMealItem.growCrop(bonemeal, helper.getLevel(), helper.absolutePos(WHERE));

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

    @HsGameTest
    public static void gatedSaplingDoesNotBecomeTree(GameTestHelper helper) {
        try {
            cropStage("crop_sapling", List.of("#minecraft:saplings"));
            helper.setBlock(WHERE.below(), Blocks.DIRT.defaultBlockState());
            helper.setBlock(WHERE, Blocks.OAK_SAPLING.defaultBlockState());

            // Stage 1: the next advance hands over to the tree grower, the place the mixin guards.
            BlockState sapling = Blocks.OAK_SAPLING.defaultBlockState().setValue(SaplingBlock.STAGE, 1);
            helper.setBlock(WHERE, sapling);
            ((SaplingBlock) Blocks.OAK_SAPLING).advanceTree(helper.getLevel(), helper.absolutePos(WHERE),
                    sapling, helper.getLevel().getRandom());

            if (!helper.getBlockState(WHERE).is(Blocks.OAK_SAPLING)) {
                helper.fail("a gated sapling grew into its tree");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    // Own batch: the stage list is global, and the locked and the free cactus test would see each
    // other's stage when run side by side.
    @HsGameTest(batch = "crop_cactus_locked")
    public static void gatedCactusDoesNotGrowOnRandomTicks(GameTestHelper helper) {
        try {
            cropStage("crop_cactus", List.of("minecraft:cactus"));
            if (cactusGrewAfterRandomTicks(helper)) {
                helper.fail("cactus is gated by a locked stage but grew on its random ticks");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    // Own batch: the stage list is global, and the locked and the free cactus test would see each
    // other's stage when run side by side.
    @HsGameTest(batch = "crop_cactus_free")
    public static void ungatedCactusGrowsOnRandomTicks(GameTestHelper helper) {
        try {
            // A crop lock exists, so the random-tick hook is active, but not for cactus.
            cropStage("crop_not_cactus", List.of("minecraft:wheat"));
            if (!cactusGrewAfterRandomTicks(helper)) {
                helper.fail("only wheat is gated, but cactus did not grow on twenty random ticks");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }
}
