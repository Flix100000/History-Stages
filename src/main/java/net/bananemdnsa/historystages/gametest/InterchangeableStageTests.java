package net.bananemdnsa.historystages.gametest;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Interchangeable stages against a real item and a real dimension.
 *
 * <p>Each positive case has a control next to it that differs only in one stage's flag. Without
 * it a test that passes could just as well mean "unlocking anything frees everything", which is
 * the bug this feature would most likely have.
 */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InterchangeableStageTests {

    private static final String ITEM = "minecraft:diamond_sword";
    private static final String DIMENSION = "minecraft:the_nether";

    private InterchangeableStageTests() {}

    @GameTest(template = "empty")
    public static void oneUnlockedInterchangeableStageFreesTheItem(GameTestHelper helper) {
        StageData data = StageData.get(helper.getLevel());
        String magic = GameTestStages.PREFIX + "or_magic";
        try {
            itemStage("or_magic", true);
            itemStage("or_mining", true);
            ServerPlayer player = GameTestPlayers.create(helper);
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);

            if (!StageLockHelper.isItemLockedForPlayer(sword, player.getUUID())) {
                helper.fail("neither interchangeable stage is unlocked, the item must start locked");
                return;
            }
            data.addStage(magic);
            if (StageLockHelper.isItemLockedForPlayer(sword, player.getUUID())) {
                helper.fail("one of two interchangeable stages is unlocked, the item must be free");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            data.removeStage(magic);
        }
    }

    @GameTest(template = "empty")
    public static void aStageWithoutTheFlagKeepsTheItemLocked(GameTestHelper helper) {
        StageData data = StageData.get(helper.getLevel());
        String magic = GameTestStages.PREFIX + "or_magic";
        try {
            itemStage("or_magic", true);
            itemStage("and_endgame", false);
            data.addStage(magic);
            ServerPlayer player = GameTestPlayers.create(helper);

            if (!StageLockHelper.isItemLockedForPlayer(new ItemStack(Items.DIAMOND_SWORD), player.getUUID())) {
                helper.fail("the flagged stage is unlocked but the unflagged one is not; "
                        + "the unflagged stage must still lock the item");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            data.removeStage(magic);
        }
    }

    @GameTest(template = "empty")
    public static void theRuleHoldsOutsideItemsToo(GameTestHelper helper) {
        // Dimensions take the category path, not the item index; both have to agree.
        StageData data = StageData.get(helper.getLevel());
        String magic = GameTestStages.PREFIX + "or_magic";
        try {
            dimensionStage("or_magic", true);
            dimensionStage("or_mining", true);
            dimensionStage("and_endgame", false);
            data.addStage(magic);
            ServerPlayer player = GameTestPlayers.create(helper);

            if (!StageLockHelper.isDimensionLockedForPlayer(DIMENSION, player.getUUID())) {
                helper.fail("the unflagged stage is still locked, the dimension must be too");
                return;
            }
            data.addStage(GameTestStages.PREFIX + "and_endgame");
            if (StageLockHelper.isDimensionLockedForPlayer(DIMENSION, player.getUUID())) {
                helper.fail("(or_magic OR or_mining) AND and_endgame is met, the dimension must be free");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            data.removeStage(magic);
            data.removeStage(GameTestStages.PREFIX + "and_endgame");
        }
    }

    private static void itemStage(String name, boolean interchangeable) {
        GameTestStages.global(name, stage -> {
            stage.setItemEntries(new ArrayList<>(List.of(new ItemEntry(ITEM))));
            stage.setInterchangeable(interchangeable);
        });
    }

    private static void dimensionStage(String name, boolean interchangeable) {
        GameTestStages.global(name, stage -> {
            stage.setDimensions(new ArrayList<>(List.of(DIMENSION)));
            stage.setInterchangeable(interchangeable);
        });
    }
}
