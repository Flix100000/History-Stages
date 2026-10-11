package net.bananemdnsa.historystages.gametest;

import java.util.List;

import com.google.gson.JsonObject;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.stage.StageStateView;
import net.bananemdnsa.historystages.api.stage.StageStates;
import net.bananemdnsa.historystages.data.display.HiddenDisplayConfig;
import net.bananemdnsa.historystages.data.logic.Condition;
import net.bananemdnsa.historystages.data.logic.LogicBlock;
import net.bananemdnsa.historystages.data.logic.LogicBlockTypes;
import net.bananemdnsa.historystages.data.logic.LogicCodec;
import net.bananemdnsa.historystages.data.logic.StageLogicGate;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The read side of {@link StageStates}: what an addon sees when it asks whether a stage is open.
 *
 * <p>The saved data is written directly rather than through the unlock methods. Those are covered
 * elsewhere, and going through them here would add a recipe reload per test for nothing.
 */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StageQueryTests {

    private static final String GLOBAL = GameTestStages.PREFIX + "query_global";
    private static final String INDIVIDUAL = GameTestStages.PREFIX + "query_individual";

    private StageQueryTests() {}

    @GameTest(template = "empty")
    public static void globalQueryFollowsTheSavedData(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StageData data = StageData.get(level);
        try {
            // Taken before the unlock on purpose: a view that copied the set would miss it.
            StageStateView view = StageStates.globalView(level);
            if (StageStates.isUnlockedGlobal(GLOBAL, level) || view.isUnlocked(GLOBAL)) {
                helper.fail("a stage nobody unlocked must read as locked");
                return;
            }
            data.addStage(GLOBAL);
            if (!StageStates.isUnlockedGlobal(GLOBAL, level) || !view.isUnlocked(GLOBAL)) {
                helper.fail("after the unlock both the query and the held view must read it as open");
                return;
            }
            data.removeStage(GLOBAL);
            if (StageStates.isUnlockedGlobal(GLOBAL, level) || view.isUnlocked(GLOBAL)) {
                helper.fail("after the relock both must read it as locked again");
                return;
            }
            helper.succeed();
        } finally {
            data.removeStage(GLOBAL);
        }
    }

    @GameTest(template = "empty")
    public static void individualQueryIsPerPlayer(GameTestHelper helper) {
        ServerPlayer owner = GameTestPlayers.create(helper);
        ServerPlayer other = GameTestPlayers.create(helper);
        IndividualStageData data = IndividualStageData.get(helper.getLevel());
        try {
            StageStateView ownerView = StageStates.individualView(owner);
            StageStateView otherView = StageStates.individualView(other);
            data.addStage(owner.getUUID(), INDIVIDUAL);

            if (!StageStates.isUnlockedIndividual(INDIVIDUAL, owner) || !ownerView.isUnlocked(INDIVIDUAL)) {
                helper.fail("the player who has the stage must read it as open, through the query and the held view");
                return;
            }
            if (StageStates.isUnlockedIndividual(INDIVIDUAL, other) || otherView.isUnlocked(INDIVIDUAL)) {
                helper.fail("another player's stage must not leak into this player's answer");
                return;
            }
            if (StageStates.isUnlockedGlobal(INDIVIDUAL, helper.getLevel())) {
                helper.fail("an individual stage must not read as a global one");
                return;
            }
            helper.succeed();
        } finally {
            data.removeStage(owner.getUUID(), INDIVIDUAL);
        }
    }

    @GameTest(template = "empty")
    public static void anyScopeQueryPicksTheRightTree(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = GameTestPlayers.create(helper);
        StageData global = StageData.get(level);
        IndividualStageData individual = IndividualStageData.get(level);
        try {
            GameTestStages.global("query_global");
            GameTestStages.individual("query_individual");

            if (StageStates.scopeOf(GLOBAL) != StageScope.GLOBAL
                    || StageStates.scopeOf(INDIVIDUAL) != StageScope.INDIVIDUAL
                    || StageStates.scopeOf(GameTestStages.PREFIX + "nowhere") != null) {
                helper.fail("scopeOf must name the tree each id lives in, and null for an unknown one");
                return;
            }
            global.addStage(GLOBAL);
            individual.addStage(player.getUUID(), INDIVIDUAL);
            if (!StageStates.isUnlockedFor(GLOBAL, player) || !StageStates.isUnlockedFor(INDIVIDUAL, player)) {
                helper.fail("isUnlockedFor must read the world for a global stage and the player for an individual one");
                return;
            }
            // The control: the same id unlocked in the wrong tree must not count.
            individual.removeStage(player.getUUID(), INDIVIDUAL);
            global.addStage(INDIVIDUAL);
            if (StageStates.isUnlockedFor(INDIVIDUAL, player)) {
                helper.fail("an individual stage must not read as open because the world has the same id");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            global.removeStage(GLOBAL);
            global.removeStage(INDIVIDUAL);
            individual.removeStage(player.getUUID(), INDIVIDUAL);
        }
    }

    @GameTest(template = "empty")
    public static void displayNameHidesWhatTheLogicHides(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.create(helper);
        StageData data = StageData.get(helper.getLevel());
        String gate = GameTestStages.PREFIX + "query_gate";
        try {
            GameTestStages.global("query_gate");
            GameTestStages.global("query_global", stage -> stage.setLogic(LogicCodec.write(List.of(
                    new LogicBlock(LogicBlockTypes.HIDDEN_WHILE,
                            new Condition.Not(new Condition.Unlocked(gate, null)), new JsonObject())))));

            String shown = StageStates.displayNameFor(GLOBAL, player);
            if (!StageLogicGate.HIDDEN_NAME.equals(shown)) {
                helper.fail("a stage hidden by its logic must not give its name away; got '" + shown + "'");
                return;
            }
            data.addStage(gate);
            shown = StageStates.displayNameFor(GLOBAL, player);
            if (!"query_global".equals(shown)) {
                helper.fail("once the gate is open the real name must show; got '" + shown + "'");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            data.removeStage(gate);
        }
    }

    @GameTest(template = "empty")
    public static void lockHintsFollowTheStageSetting(GameTestHelper helper) {
        try {
            GameTestStages.global("query_global", stage -> {
                HiddenDisplayConfig hidden = new HiddenDisplayConfig();
                hidden.setShowLockHints(false);
                stage.setHiddenDisplay(hidden);
            });
            GameTestStages.individual("query_individual");

            if (StageStates.showsLockHints(GLOBAL)) {
                helper.fail("a stage with show_lock_hints off must say so");
                return;
            }
            if (!StageStates.showsLockHints(INDIVIDUAL)) {
                helper.fail("a stage without the setting shows its hints, as the mod always has");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }
}
