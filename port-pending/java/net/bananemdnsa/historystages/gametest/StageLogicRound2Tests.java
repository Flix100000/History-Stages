package net.bananemdnsa.historystages.gametest;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.dependency.DependencyChecker;
import net.bananemdnsa.historystages.api.dependency.RequirementResult;
import net.bananemdnsa.historystages.data.dependency.XpLevelDep;
import net.bananemdnsa.historystages.data.logic.Condition;
import net.bananemdnsa.historystages.data.logic.LogicBlock;
import net.bananemdnsa.historystages.data.logic.LogicBlockTypes;
import net.bananemdnsa.historystages.data.logic.LogicCodec;
import net.bananemdnsa.historystages.data.logic.StageLogic;
import net.bananemdnsa.historystages.data.logic.StageLogicGate;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * "Hidden while" and "cheaper/costlier while" against the real stage store.
 *
 * <p>The unit tests settle the arithmetic; these settle that the server really answers with it —
 * that a requirement naming a hidden stage says "???", and that cost factors reach the requirement
 * a player is shown.
 */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StageLogicRound2Tests {

    private static final String P = GameTestStages.PREFIX;

    private StageLogicRound2Tests() {}

    private static LogicBlock block(String type, Condition condition, JsonObject extra) {
        JsonObject raw = extra == null ? new JsonObject() : extra;
        return new LogicBlock(type, condition, raw);
    }

    @GameTest(template = "empty")
    public static void aRequirementNamingAHiddenStageSaysQuestionMarks(GameTestHelper helper) {
        StageData data = StageData.get(helper.getLevel());
        try {
            GameTestStages.global("gate");
            GameTestStages.global("secret", stage -> stage.setLogic(LogicCodec.write(List.of(
                    block(LogicBlockTypes.HIDDEN_WHILE, new Condition.Not(new Condition.Unlocked(P + "gate", null)), null)))));
            DependencyGroup needsSecret = new DependencyGroup();
            needsSecret.setStages(new ArrayList<>(List.of(P + "secret")));
            GameTestStages.global("after", needsSecret);
            ServerPlayer player = GameTestPlayers.create(helper);

            String shown = requirementName(helper, player);
            if (!StageLogicGate.HIDDEN_NAME.equals(shown)) {
                helper.fail("the required stage is hidden until the gate opens; the requirement showed '" + shown + "'");
                return;
            }
            data.addStage(P + "gate");
            shown = requirementName(helper, player);
            if (!"secret".equals(shown)) {
                helper.fail("the gate is open, the requirement must show the real name, got '" + shown + "'");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            data.removeStage(P + "gate");
        }
    }

    private static String requirementName(GameTestHelper helper, ServerPlayer player) {
        RequirementResult result = DependencyChecker.checkAll(
                net.bananemdnsa.historystages.data.StageManager.getStages().get(P + "after"),
                player, helper.getLevel(), StageScope.GLOBAL, null);
        return result.getGroups().get(0).getEntries().get(0).getDescription();
    }

    @GameTest(template = "empty")
    public static void costFactorsFollowTheCondition(GameTestHelper helper) {
        StageData data = StageData.get(helper.getLevel());
        try {
            GameTestStages.global("fire_master");
            JsonObject percents = new JsonObject();
            percents.addProperty("time", 50);
            percents.addProperty("items", 25);
            GameTestStages.global("ice_master", stage -> stage.setLogic(LogicCodec.write(List.of(
                    block(LogicBlockTypes.COST_WHILE, new Condition.Unlocked(P + "fire_master", null), percents)))));

            StageLogic.CostFactors before = StageLogicGate.cost(P + "ice_master", false, null);
            if (before.time() != 1.0 || before.items() != 1.0) {
                helper.fail("fire is not mastered, nothing may be cheaper yet, got " + before);
                return;
            }
            data.addStage(P + "fire_master");
            StageLogic.CostFactors after = StageLogicGate.cost(P + "ice_master", false, null);
            if (after.time() != 0.5 || after.items() != 0.25) {
                helper.fail("fire is mastered, time 50 % and items 25 % expected, got " + after);
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            data.removeStage(P + "fire_master");
        }
    }

    @GameTest(template = "empty")
    public static void theXpFactorReachesTheRequirement(GameTestHelper helper) {
        try {
            DependencyGroup group = new DependencyGroup();
            group.setXpLevel(new XpLevelDep(10, true));
            GameTestStages.individual("scholar", group);
            ServerPlayer player = GameTestPlayers.create(helper);

            RequirementResult result = DependencyChecker.checkAll(
                    net.bananemdnsa.historystages.data.StageManager.getIndividualStages().get(P + "scholar"),
                    player, helper.getLevel(), StageScope.INDIVIDUAL, null, 0.0, 0.5);
            int required = result.getGroups().get(0).getEntries().get(0).getRequired();
            if (required != 5) {
                helper.fail("10 levels at 50 % must ask for 5, asked for " + required);
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }
}
