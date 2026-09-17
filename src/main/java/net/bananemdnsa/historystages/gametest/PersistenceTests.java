package net.bananemdnsa.historystages.gametest;

import java.util.UUID;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Stage state written and read back, through the real SavedData machinery.
 *
 * <p>Half of persistence. The other half is a restart, and a GameTest lives in one session — so
 * what this catches is a broken write or a broken read, not a stage that fails to survive a stop
 * and a start. That one is a human's job and the design says so, rather than leaving it to look
 * covered.
 *
 * <p>The individual half is tested beside the global one on purpose. Forgetting individual stages
 * is this project's most repeated mistake: feature after feature has handled the global map and
 * left the per-player one behind, and each time it was found in game rather than here.
 */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PersistenceTests {

    private PersistenceTests() {}

    @GameTest(template = "empty")
    public static void aGlobalStageIsReadBackAsUnlocked(GameTestHelper helper) {
        StageData data = StageData.get(helper.getLevel());
        String id = GameTestStages.PREFIX + "persisted";
        try {
            if (data.hasStage(id)) {
                helper.fail("the test stage was already unlocked before this test ran, "
                        + "so an earlier test leaked it");
                return;
            }

            data.addStage(id);

            if (!data.hasStage(id)) {
                helper.fail("the stage was added and hasStage still reports it as locked");
                return;
            }
            helper.succeed();
        } finally {
            data.removeStage(id);
        }
    }

    @GameTest(template = "empty")
    public static void aRemovedGlobalStageIsReadBackAsLocked(GameTestHelper helper) {
        StageData data = StageData.get(helper.getLevel());
        String id = GameTestStages.PREFIX + "removed";
        try {
            data.addStage(id);
            data.removeStage(id);

            if (data.hasStage(id)) {
                helper.fail("the stage was removed and hasStage still reports it as unlocked");
                return;
            }
            helper.succeed();
        } finally {
            data.removeStage(id);
        }
    }

    @GameTest(template = "empty")
    public static void anIndividualStageIsReadBackForThatPlayerOnly(GameTestHelper helper) {
        IndividualStageData data = IndividualStageData.get(helper.getLevel());
        String id = GameTestStages.PREFIX + "individual";
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        try {
            data.addStage(first, id);

            if (!data.hasStage(first, id)) {
                helper.fail("the stage was unlocked for the first player "
                        + "and hasStage still reports it as locked for them");
                return;
            }
            // The half that gets forgotten: unlocking for one player must not unlock for everyone.
            if (data.hasStage(second, id)) {
                helper.fail("the stage was unlocked for one player "
                        + "and is reported as unlocked for a different one");
                return;
            }
            helper.succeed();
        } finally {
            data.removeStage(first, id);
            data.removeStage(second, id);
        }
    }

    @GameTest(template = "empty")
    public static void aRemovedIndividualStageIsReadBackAsLocked(GameTestHelper helper) {
        IndividualStageData data = IndividualStageData.get(helper.getLevel());
        String id = GameTestStages.PREFIX + "individual_removed";
        UUID player = UUID.randomUUID();
        try {
            data.addStage(player, id);
            data.removeStage(player, id);

            if (data.hasStage(player, id)) {
                helper.fail("the stage was removed for the player "
                        + "and hasStage still reports it as unlocked");
                return;
            }
            helper.succeed();
        } finally {
            data.removeStage(player, id);
        }
    }

    @GameTest(template = "empty")
    public static void aGlobalUnlockKeepsItsFirstTimeUntilRemoved(GameTestHelper helper) {
        StageData data = StageData.get(helper.getLevel());
        String id = GameTestStages.PREFIX + "timed";
        data.addStage(id);
        Long first = data.getUnlockTimes().get(id);
        if (first == null) {
            data.removeStage(id);
            helper.fail("unlocking a stage recorded no unlock time");
            return;
        }
        // The map background goes to the latest unlock, so a repeated grant must not make an
        // old stage look new. A few ticks apart, or a re-stamp would write the same time back.
        helper.runAfterDelay(5, () -> {
            try {
                data.addStage(id);
                if (!first.equals(data.getUnlockTimes().get(id))) {
                    helper.fail("unlocking an already unlocked stage changed its time from " + first
                            + " to " + data.getUnlockTimes().get(id));
                    return;
                }
                if (!first.equals(StageData.SERVER_UNLOCK_TIMES.get(id))) {
                    helper.fail("the sync mirror holds " + StageData.SERVER_UNLOCK_TIMES.get(id)
                            + " instead of " + first);
                    return;
                }
                data.removeStage(id);
                if (data.getUnlockTimes().containsKey(id) || StageData.SERVER_UNLOCK_TIMES.containsKey(id)) {
                    helper.fail("a removed stage kept its unlock time");
                    return;
                }
                helper.succeed();
            } finally {
                data.removeStage(id);
            }
        });
    }

    @GameTest(template = "empty")
    public static void anIndividualUnlockKeepsItsFirstTimeUntilRemoved(GameTestHelper helper) {
        IndividualStageData data = IndividualStageData.get(helper.getLevel());
        String id = GameTestStages.PREFIX + "individual_timed";
        UUID player = UUID.randomUUID();
        data.addStage(player, id);
        Long first = data.getUnlockTimes(player).get(id);
        if (first == null) {
            data.removeStage(player, id);
            helper.fail("unlocking an individual stage recorded no unlock time");
            return;
        }
        helper.runAfterDelay(5, () -> {
            try {
                data.addStage(player, id);
                if (!first.equals(data.getUnlockTimes(player).get(id))) {
                    helper.fail("unlocking an already unlocked individual stage changed its time from "
                            + first + " to " + data.getUnlockTimes(player).get(id));
                    return;
                }
                data.removeStage(player, id);
                if (data.getUnlockTimes(player).containsKey(id)) {
                    helper.fail("a removed individual stage kept its unlock time");
                    return;
                }
                helper.succeed();
            } finally {
                data.removeStage(player, id);
            }
        });
    }

    // Own batch: load() rebuilds the shared server caches from its copy, and tests in the same
    // batch run in the same ticks.
    @GameTest(template = "empty", batch = "unlock_times_round_trip")
    public static void unlockTimesSurviveAWriteAndARead(GameTestHelper helper) {
        StageData global = StageData.get(helper.getLevel());
        IndividualStageData individual = IndividualStageData.get(helper.getLevel());
                String id = GameTestStages.PREFIX + "timed_saved";
        UUID player = UUID.randomUUID();
        try {
            global.addStage(id);
            individual.addStage(player, id);

            Long globalTime = StageData.load(global.save(new CompoundTag()))
                    .getUnlockTimes().get(id);
            Long individualTime = IndividualStageData.load(individual.save(new CompoundTag()))
                    .getUnlockTimes(player).get(id);

            if (!global.getUnlockTimes().get(id).equals(globalTime)) {
                helper.fail("the global unlock time came back as " + globalTime
                        + " instead of " + global.getUnlockTimes().get(id));
                return;
            }
            if (!individual.getUnlockTimes(player).get(id).equals(individualTime)) {
                helper.fail("the individual unlock time came back as " + individualTime
                        + " instead of " + individual.getUnlockTimes(player).get(id));
                return;
            }
            helper.succeed();
        } finally {
            global.removeStage(id);
            individual.removeStage(player, id);
            // load() rebuilt the static caches from a copy; get() puts them back on the live data.
            StageData.get(helper.getLevel());
            IndividualStageData.get(helper.getLevel());
        }
    }

    @GameTest(template = "empty")
    public static void theGlobalAndIndividualStoresDoNotSeeEachOther(GameTestHelper helper) {
        StageData global = StageData.get(helper.getLevel());
        IndividualStageData individual = IndividualStageData.get(helper.getLevel());
        String id = GameTestStages.PREFIX + "crosstalk";
        UUID player = UUID.randomUUID();
        try {
            global.addStage(id);

            // Two stores under the same id. A shared backing map would make this pass by accident
            // and hand every player a stage the pack meant to grant one at a time.
            if (individual.hasStage(player, id)) {
                helper.fail("a globally unlocked stage is reported as unlocked "
                        + "for an individual player who never received it");
                return;
            }
            helper.succeed();
        } finally {
            global.removeStage(id);
            individual.removeStage(player, id);
        }
    }
}
