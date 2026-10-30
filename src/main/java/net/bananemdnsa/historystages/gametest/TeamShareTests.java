package net.bananemdnsa.historystages.gametest;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.api.stage.StageStates;
import net.bananemdnsa.historystages.data.StageMode;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.LostStagesData;
import net.bananemdnsa.historystages.data.saveddata.TeamSharePending;
import net.bananemdnsa.historystages.data.team.TeamProviders;
import net.bananemdnsa.historystages.data.team.TeamStageSharing;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.bananemdnsa.historystages.platform.event.entity.player.PlayerEvent;

/**
 * Individual stages flagged "share with team".
 *
 * <p>Test players are never in the server's player list, so to the sharing code every teammate is
 * offline: what a teammate is owed lands in {@link TeamSharePending} and is paid out by calling
 * the login handler directly. Posting the real login event would wake FTB Quests, which then
 * tries to talk to a player without a connection.
 */
public class TeamShareTests {

    private static final String P = GameTestStages.PREFIX;

    public TeamShareTests() {}

    /** Puts every given player in one team for the duration of a test. */
    private static void team(ServerPlayer... players) {
        Set<UUID> ids = new java.util.HashSet<>();
        for (ServerPlayer p : players) ids.add(p.getUUID());
        TeamProviders.overrideForTesting((server, player) -> ids.contains(player) ? Set.copyOf(ids) : Set.of());
    }

    private static void login(ServerPlayer player) {
        TeamStageSharing.onLogin(new PlayerEvent.PlayerLoggedInEvent(player));
    }

    /** Everything but the stages, which each test removes itself so the cleanup guard can see it. */
    private static void cleanUp(GameTestHelper helper, List<String> stages, ServerPlayer... players) {
        TeamProviders.overrideForTesting(null);
        IndividualStageData data = IndividualStageData.get(helper.getLevel());
        TeamSharePending pending = TeamSharePending.get(helper.getLevel());
        LostStagesData lost = LostStagesData.get(helper.getLevel());
        for (ServerPlayer p : players) {
            pending.takeGrants(p.getUUID());
            pending.takeLosses(p.getUUID());
            for (String id : stages) {
                data.removeStage(p.getUUID(), P + id);
                lost.clearIndividual(p.getUUID(), P + id);
            }
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void aSharedStageReachesTheTeammate(GameTestHelper helper) {
        IndividualStageData data = IndividualStageData.get(helper.getLevel());
        ServerPlayer alice = GameTestPlayers.createConnected(helper);
        ServerPlayer bob = GameTestPlayers.createConnected(helper);
        try {
            GameTestStages.individual("forge", stage -> stage.setShareWithTeam(true));
            GameTestStages.individual("diary");
            team(alice, bob);

            StageStates.unlockIndividual(P + "forge", alice);
            StageStates.unlockIndividual(P + "diary", alice);
            login(bob);

            if (!data.hasStage(bob.getUUID(), P + "forge")) {
                helper.fail("the shared stage must reach the teammate");
                return;
            }
            if (data.hasStage(bob.getUUID(), P + "diary")) {
                helper.fail("a stage without the flag must stay with the player who unlocked it");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            cleanUp(helper, List.of("forge", "diary"), alice, bob);
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void aTeammateWhoLostItForGoodStaysLocked(GameTestHelper helper) {
        IndividualStageData data = IndividualStageData.get(helper.getLevel());
        ServerPlayer alice = GameTestPlayers.createConnected(helper);
        ServerPlayer bob = GameTestPlayers.createConnected(helper);
        try {
            GameTestStages.individual("forge", stage -> stage.setShareWithTeam(true));
            LostStagesData.get(helper.getLevel()).markIndividual(bob.getUUID(), P + "forge");
            team(alice, bob);

            StageStates.unlockIndividual(P + "forge", alice);
            login(bob);

            if (data.hasStage(bob.getUUID(), P + "forge")) {
                helper.fail("a stage lost for good must stay locked for that teammate");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            cleanUp(helper, List.of("forge"), alice, bob);
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void anAdminUnlockStaysWithOnePlayer(GameTestHelper helper) {
        IndividualStageData data = IndividualStageData.get(helper.getLevel());
        ServerPlayer alice = GameTestPlayers.createConnected(helper);
        ServerPlayer bob = GameTestPlayers.createConnected(helper);
        try {
            GameTestStages.individual("forge", stage -> stage.setShareWithTeam(true));
            team(alice, bob);

            StageStates.forceUnlockIndividual(P + "forge", alice);
            login(bob);

            if (!data.hasStage(alice.getUUID(), P + "forge")) {
                helper.fail("the admin unlock itself must still work");
                return;
            }
            if (data.hasStage(bob.getUUID(), P + "forge")) {
                helper.fail("an admin unlock means that one player; the team must not get it");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            cleanUp(helper, List.of("forge"), alice, bob);
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void aTemporaryStageIsNeverShared(GameTestHelper helper) {
        IndividualStageData data = IndividualStageData.get(helper.getLevel());
        ServerPlayer alice = GameTestPlayers.createConnected(helper);
        ServerPlayer bob = GameTestPlayers.createConnected(helper);
        try {
            GameTestStages.individual("rush", stage -> {
                stage.setShareWithTeam(true);
                stage.setMode(StageMode.TEMPORARY);
            });
            team(alice, bob);

            StageStates.unlockIndividual(P + "rush", alice);
            login(bob);

            if (data.hasStage(bob.getUUID(), P + "rush")) {
                helper.fail("a temporary stage runs on one player's timer and must not be shared");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            cleanUp(helper, List.of("rush"), alice, bob);
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void losingIsSharedOnlyWhenSwitchedOn(GameTestHelper helper) {
        IndividualStageData data = IndividualStageData.get(helper.getLevel());
        ServerPlayer alice = GameTestPlayers.createConnected(helper);
        ServerPlayer bob = GameTestPlayers.createConnected(helper);
        boolean original = Config.GAMEPLAY.teamShareLoss.get();
        try {
            GameTestStages.individual("forge", stage -> stage.setShareWithTeam(true));
            team(alice, bob);
            data.addStage(alice.getUUID(), P + "forge");
            data.addStage(bob.getUUID(), P + "forge");

            Config.GAMEPLAY.teamShareLoss.set(false);
            StageStates.relockIndividual(P + "forge", alice);
            login(bob);
            if (!data.hasStage(bob.getUUID(), P + "forge")) {
                helper.fail("with the switch off, one player's loss must not reach the team");
                return;
            }

            data.addStage(alice.getUUID(), P + "forge");
            Config.GAMEPLAY.teamShareLoss.set(true);
            StageStates.relockIndividual(P + "forge", alice);
            login(bob);
            if (data.hasStage(bob.getUUID(), P + "forge")) {
                helper.fail("with the switch on, the team loses the stage together");
                return;
            }
            if (LostStagesData.get(helper.getLevel()).isLostIndividual(bob.getUUID(), P + "forge")) {
                helper.fail("a teammate loses the stage the normal way, never for good");
                return;
            }
            helper.succeed();
        } finally {
            Config.GAMEPLAY.teamShareLoss.set(original);
            GameTestStages.removeAll();
            cleanUp(helper, List.of("forge"), alice, bob);
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void aNewMemberCatchesUpOnlyWhenSwitchedOn(GameTestHelper helper) {
        IndividualStageData data = IndividualStageData.get(helper.getLevel());
        ServerPlayer alice = GameTestPlayers.createConnected(helper);
        ServerPlayer bob = GameTestPlayers.createConnected(helper);
        boolean original = Config.GAMEPLAY.teamShareOnJoin.get();
        try {
            GameTestStages.individual("forge", stage -> stage.setShareWithTeam(true));
            // Alice had it before Bob joined, so nothing was ever queued for him.
            data.addStage(alice.getUUID(), P + "forge");
            team(alice, bob);

            Config.GAMEPLAY.teamShareOnJoin.set(false);
            TeamStageSharing.onJoinedTeam(bob);
            if (data.hasStage(bob.getUUID(), P + "forge")) {
                helper.fail("with the switch off, joining a team hands over nothing");
                return;
            }

            Config.GAMEPLAY.teamShareOnJoin.set(true);
            TeamStageSharing.onJoinedTeam(bob);
            if (!data.hasStage(bob.getUUID(), P + "forge")) {
                helper.fail("with the switch on, a new member gets what the team already has");
                return;
            }
            helper.succeed();
        } finally {
            Config.GAMEPLAY.teamShareOnJoin.set(original);
            GameTestStages.removeAll();
            cleanUp(helper, List.of("forge"), alice, bob);
        }
    }
}
