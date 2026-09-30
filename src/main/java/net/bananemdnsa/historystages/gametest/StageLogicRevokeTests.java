package net.bananemdnsa.historystages.gametest;

import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.api.stage.StageEvent;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.stage.StageStates;
import net.bananemdnsa.historystages.data.logic.Condition;
import net.bananemdnsa.historystages.data.logic.LogicBlock;
import net.bananemdnsa.historystages.data.logic.LogicBlockTypes;
import net.bananemdnsa.historystages.data.logic.LogicCodec;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.LogicPendingRevocations;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * "Revoke when" against the real unlock paths: it fires once on the jump, chains, and waits for
 * players who are offline.
 */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StageLogicRevokeTests {

    private static final String P = GameTestStages.PREFIX;

    private StageLogicRevokeTests() {}

    private static void revokeWhen(net.bananemdnsa.historystages.data.StageEntry stage, Condition condition) {
        stage.setLogic(LogicCodec.write(List.of(new LogicBlock(LogicBlockTypes.REVOKE_WHEN, condition, new JsonObject()))));
    }

    @GameTest(template = "empty")
    public static void takingThePactTakesTheLightAwayOnce(GameTestHelper helper) {
        IndividualStageData data = IndividualStageData.get(helper.getLevel());
        ServerPlayer player = GameTestPlayers.createConnected(helper);
        try {
            GameTestStages.individual("shadow_pact");
            GameTestStages.individual("light_path", stage -> revokeWhen(stage, new Condition.Unlocked(P + "shadow_pact", null)));
            data.addStage(player.getUUID(), P + "light_path");

            StageStates.unlockIndividual(P + "shadow_pact", player);
            if (data.hasStage(player.getUUID(), P + "light_path")) {
                helper.fail("unlocking the pact must take the light path away");
                return;
            }
            // Once only: the pact still stands, but researching the light again must stick.
            StageStates.unlockIndividual(P + "light_path", player);
            if (!data.hasStage(player.getUUID(), P + "light_path")) {
                helper.fail("the block fires once; the light path must be researchable again");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            data.removeStage(player.getUUID(), P + "light_path");
            data.removeStage(player.getUUID(), P + "shadow_pact");
        }
    }

    @GameTest(template = "empty")
    public static void aRevocationCanCauseTheNext(GameTestHelper helper) {
        IndividualStageData data = IndividualStageData.get(helper.getLevel());
        ServerPlayer player = GameTestPlayers.createConnected(helper);
        try {
            GameTestStages.individual("trigger");
            GameTestStages.individual("first", stage -> revokeWhen(stage, new Condition.Unlocked(P + "trigger", null)));
            GameTestStages.individual("second", stage -> revokeWhen(stage,
                    new Condition.Not(new Condition.Unlocked(P + "first", null))));
            data.addStage(player.getUUID(), P + "first");
            data.addStage(player.getUUID(), P + "second");

            StageStates.unlockIndividual(P + "trigger", player);
            if (data.hasStage(player.getUUID(), P + "first") || data.hasStage(player.getUUID(), P + "second")) {
                helper.fail("trigger takes 'first', and losing 'first' takes 'second'; both must be gone");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            for (String id : List.of("trigger", "first", "second")) data.removeStage(player.getUUID(), P + id);
        }
    }

    @GameTest(template = "empty")
    public static void anOfflinePlayerIsOwedTheRevocation(GameTestHelper helper) {
        StageData global = StageData.get(helper.getLevel());
        IndividualStageData individual = IndividualStageData.get(helper.getLevel());
        LogicPendingRevocations pending = LogicPendingRevocations.get(helper.getLevel());
        UUID offline = UUID.randomUUID();
        try {
            GameTestStages.global("shadow_age");
            GameTestStages.individual("old_ways", stage -> revokeWhen(stage,
                    new Condition.Unlocked(P + "shadow_age", StageScope.GLOBAL)));
            individual.addStage(offline, P + "old_ways");

            // How the pedestal unlocks a global stage: write it, then post the event.
            global.addStage(P + "shadow_age");
            NeoForge.EVENT_BUS.post(new StageEvent.Unlocked(P + "shadow_age", "shadow_age"));

            if (!pending.has(offline, P + "old_ways")) {
                helper.fail("the player was offline when the global stage opened; the revocation must be queued");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            global.removeStage(P + "shadow_age");
            individual.removeStage(offline, P + "old_ways");
            pending.take(offline);
        }
    }
}
