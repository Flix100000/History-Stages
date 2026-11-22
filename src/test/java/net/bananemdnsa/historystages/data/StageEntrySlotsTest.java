package net.bananemdnsa.historystages.data;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import net.bananemdnsa.historystages.data.lock.SlotLockEntry;
import net.bananemdnsa.historystages.data.lock.SlotLockMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageEntrySlotsTest {

    private static final Gson GSON = new Gson();

    @Test
    void roundTripKeepsTargetSlotsAndMode() {
        StageEntry stage = GSON.fromJson(
                "{\"slots\":[{\"target\":\"minecraft:barrel\",\"slots\":[18,19],\"mode\":\"hide\"}]}",
                StageEntry.class);
        StageEntry reloaded = GSON.fromJson(stage.toJson(), StageEntry.class);

        SlotLockEntry entry = reloaded.getSlotLocks().get(0);
        assertEquals("minecraft:barrel", entry.target());
        assertEquals(List.of(18, 19), entry.slots());
        assertEquals(SlotLockMode.HIDE, entry.mode());
    }

    @Test
    void missingModeMeansOverlay() {
        StageEntry stage = GSON.fromJson(
                "{\"slots\":[{\"target\":\"player\",\"slots\":[0]}]}", StageEntry.class);
        SlotLockEntry entry = stage.getSlotLocks().get(0);
        assertEquals(SlotLockMode.OVERLAY, entry.mode());
        assertTrue(entry.isPlayerTarget());
    }

    @Test
    void emptySlotLocksAreNotWritten() {
        StageEntry stage = new StageEntry();
        stage.setSlotLocks(List.of());
        assertFalse(JsonParser.parseString(stage.toJson()).getAsJsonObject().has("slots"));
    }

    @Test
    void copyIsDeep() {
        StageEntry original = new StageEntry();
        original.setSlotLocks(List.of(new SlotLockEntry("player", new ArrayList<>(List.of(1, 2)), SlotLockMode.HIDE)));

        StageEntry copy = original.copy();
        original.getSlotLocks().get(0).slots().add(3);

        assertEquals(List.of(1, 2), copy.getSlotLocks().get(0).slots());
    }

    @Test
    void indexKeysJoinTargetAndSlot() {
        SlotLockEntry entry = new SlotLockEntry("player", List.of(3, 40), SlotLockMode.OVERLAY);
        assertEquals(List.of("player#3", "player#40"), entry.indexKeys());
    }
}
