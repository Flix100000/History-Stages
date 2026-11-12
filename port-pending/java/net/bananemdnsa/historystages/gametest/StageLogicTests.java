package net.bananemdnsa.historystages.gametest;

import java.util.List;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.stage.StageStates;
import net.bananemdnsa.historystages.compat.script.ScriptStageApi;
import net.bananemdnsa.historystages.data.logic.Condition;
import net.bananemdnsa.historystages.data.logic.LogicBlock;
import net.bananemdnsa.historystages.data.logic.LogicBlockTypes;
import net.bananemdnsa.historystages.data.logic.LogicCodec;
import net.bananemdnsa.historystages.data.logic.StageLogicGate;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * "Blocked while" against the real stage store and the real unlock paths.
 *
 * <p>The unit tests settle what a condition means; these settle that the gate is actually in the
 * way — that a script is refused, that an admin override is not, and that a global stage never
 * reads a player's individual stages.
 */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StageLogicTests {

    private static final String P = GameTestStages.PREFIX;

    private StageLogicTests() {}

    @GameTest(template = "empty")
    public static void theMagePathOpensAgainOnceTheOtherPathIsMastered(GameTestHelper helper) {
        StageData data = StageData.get(helper.getLevel());
        try {
            GameTestStages.global("fire_1");
            GameTestStages.global("fire_3");
            GameTestStages.global("ice_1", stage -> stage.setLogic(LogicCodec.write(List.of(
                    LogicBlock.of(LogicBlockTypes.BLOCKED_WHILE, new Condition.All(List.of(
                            new Condition.Unlocked(P + "fire_1", null),
                            new Condition.Not(new Condition.Unlocked(P + "fire_3", null)))))))));

            if (StageLogicGate.global(P + "ice_1").isBlocked()) {
                helper.fail("nothing is started yet, the ice entry must be open");
                return;
            }
            data.addStage(P + "fire_1");
            if (!StageLogicGate.global(P + "ice_1").isBlocked()) {
                helper.fail("fire was started and not mastered, the ice entry must be blocked");
                return;
            }
            data.addStage(P + "fire_3");
            if (StageLogicGate.global(P + "ice_1").isBlocked()) {
                helper.fail("fire is mastered, the ice entry must be open again");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            data.removeStage(P + "fire_1");
            data.removeStage(P + "fire_3");
        }
    }

    @GameTest(template = "empty")
    public static void aScriptIsRefusedButAnAdminOverrideGoesThrough(GameTestHelper helper) {
        StageData global = StageData.get(helper.getLevel());
        IndividualStageData individual = IndividualStageData.get(helper.getLevel());
        ServerPlayer player = null;
        try {
            GameTestStages.global("world_age");
            GameTestStages.individual("apprentice", stage -> stage.setLogic(LogicCodec.write(List.of(
                    LogicBlock.of(LogicBlockTypes.BLOCKED_WHILE,
                            new Condition.Unlocked(P + "world_age", StageScope.GLOBAL))))));
            global.addStage(P + "world_age");
            // Connected: a real unlock sends the player their stage list.
            player = GameTestPlayers.createConnected(helper);

            if (ScriptStageApi.unlockFor(player, P + "apprentice")
                    || individual.hasStage(player.getUUID(), P + "apprentice")) {
                helper.fail("the individual stage is blocked by a global one; a script must not unlock it");
                return;
            }
            StageStates.UnlockOutcome outcome = StageStates.forceUnlockIndividual(P + "apprentice", player);
            if (!outcome.unlocked() || !outcome.wasBlocked()) {
                helper.fail("the admin override must unlock the stage and report that it was blocked, got "
                        + outcome);
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            global.removeStage(P + "world_age");
            if (player != null) individual.removeStage(player.getUUID(), P + "apprentice");
        }
    }

    @GameTest(template = "empty")
    public static void aGlobalStageNeverReadsAPlayersIndividualStages(GameTestHelper helper) {
        IndividualStageData individual = IndividualStageData.get(helper.getLevel());
        ServerPlayer player = null;
        try {
            GameTestStages.individual("quest");
            GameTestStages.global("guarded", stage -> stage.setLogic(LogicCodec.write(List.of(
                    LogicBlock.of(LogicBlockTypes.BLOCKED_WHILE,
                            new Condition.Unlocked(P + "quest", StageScope.INDIVIDUAL))))));
            player = GameTestPlayers.create(helper);
            individual.addStage(player.getUUID(), P + "quest");

            if (StageLogicGate.global(P + "guarded").isBlocked()) {
                helper.fail("a global stage has no player to ask; an individual term must never block it");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            if (player != null) individual.removeStage(player.getUUID(), P + "quest");
        }
    }
}
