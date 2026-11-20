package net.bananemdnsa.historystages.data.lock;

import net.bananemdnsa.historystages.data.StageEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotLockValidationTest {

    private static StageEntry stageWith(SlotLockEntry... entries) {
        StageEntry stage = new StageEntry();
        stage.setSlotLocks(List.of(entries));
        return stage;
    }

    @Test
    void individualStageLosesContainerEntries() {
        StageEntry stage = stageWith(
                new SlotLockEntry("player", List.of(0), SlotLockMode.HIDE),
                new SlotLockEntry("minecraft:barrel", List.of(0), SlotLockMode.HIDE));

        List<String> problems = SlotLockValidation.clean("s", stage, true);

        assertEquals(1, stage.getSlotLocks().size());
        assertTrue(stage.getSlotLocks().get(0).isPlayerTarget());
        assertEquals(1, problems.size());
    }

    @Test
    void globalStageKeepsContainerEntries() {
        StageEntry stage = stageWith(new SlotLockEntry("minecraft:barrel", List.of(0), SlotLockMode.HIDE));
        assertTrue(SlotLockValidation.clean("s", stage, false).isEmpty());
        assertEquals(1, stage.getSlotLocks().size());
    }

    @Test
    void playerIndicesOutsideInventoryAreDropped() {
        StageEntry stage = stageWith(new SlotLockEntry("player", List.of(-1, 0, 40, 41), SlotLockMode.HIDE));
        List<String> problems = SlotLockValidation.clean("s", stage, false);
        assertEquals(List.of(0, 40), stage.getSlotLocks().get(0).slots());
        assertEquals(2, problems.size());
    }

    @Test
    void negativeContainerIndicesAreDropped() {
        StageEntry stage = stageWith(new SlotLockEntry("minecraft:chest", List.of(-3, 53), SlotLockMode.HIDE));
        SlotLockValidation.clean("s", stage, false);
        assertEquals(List.of(53), stage.getSlotLocks().get(0).slots());
    }

    @Test
    void duplicateIndicesCollapse() {
        StageEntry stage = stageWith(new SlotLockEntry("player", List.of(3, 3, 2), SlotLockMode.HIDE));
        SlotLockValidation.clean("s", stage, false);
        assertEquals(List.of(3, 2), stage.getSlotLocks().get(0).slots());
    }

    @Test
    void entryLeftWithoutSlotsIsRemoved() {
        StageEntry stage = stageWith(new SlotLockEntry("player", List.of(99), SlotLockMode.HIDE));
        SlotLockValidation.clean("s", stage, false);
        assertTrue(stage.getSlotLocks().isEmpty());
    }
}
