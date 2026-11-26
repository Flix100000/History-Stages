package net.bananemdnsa.historystages.data.lock;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBoatIdsTest {

    private static List<EntityAttackLockEntry> readAttack(String json) {
        try {
            return new EntityAttackLockEntryListAdapter().fromJson(json);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static List<EntityInteractionLockEntry> readInteraction(String json) throws Exception {
        return new EntityInteractionLockEntryListAdapter().fromJson(json);
    }

    private static List<String> ids(List<EntityAttackLockEntry> entries) {
        return entries.stream().map(EntityAttackLockEntry::getId).toList();
    }

    @Test
    void aPlainBoatBecomesEveryWoodAndRaft() {
        List<String> ids = ids(readAttack("[\"minecraft:boat\"]"));
        assertEquals(10, ids.size());
        assertTrue(ids.contains("minecraft:oak_boat"));
        assertTrue(ids.contains("minecraft:pale_oak_boat"));
        assertTrue(ids.contains("minecraft:bamboo_raft"));
        assertTrue(ids.stream().noneMatch(id -> id.contains("chest")));
    }

    @Test
    void aChestBoatBecomesEveryChestBoatAndChestRaft() {
        List<String> ids = ids(readAttack("[\"minecraft:chest_boat\"]"));
        assertEquals(10, ids.size());
        assertTrue(ids.contains("minecraft:spruce_chest_boat"));
        assertTrue(ids.contains("minecraft:bamboo_chest_raft"));
    }

    @Test
    void theWoodsAFilterNamedBecomeTypesAndTheRestOfTheFilterStays() {
        List<EntityAttackLockEntry> entries = readAttack(
                "[{\"id\":\"minecraft:boat\",\"nbt\":[{\"Type\":\"oak\"},{\"Type\":\"birch\",\"CustomName\":\"x\"}]}]");
        assertEquals(List.of("minecraft:oak_boat", "minecraft:birch_boat"), ids(entries));
        assertTrue(entries.get(0).getNbt().isEmpty(), "oak named nothing else, so every oak boat is locked");
        JsonObject birchRest = entries.get(1).getNbt().alternatives().get(0);
        assertEquals(1, birchRest.size());
        assertEquals("x", birchRest.get("CustomName").getAsString());
    }

    @Test
    void aFilterWithoutTypeKeepsItsCriteriaOnEveryWood() {
        List<EntityAttackLockEntry> entries = readAttack("[{\"id\":\"minecraft:boat\",\"nbt\":{\"CustomName\":\"x\"}}]");
        assertEquals(10, entries.size());
        for (EntityAttackLockEntry entry : entries) {
            assertEquals("x", entry.getNbt().alternatives().get(0).get("CustomName").getAsString());
        }
    }

    @Test
    void interactionEntriesKeepTheirActionsOnEveryCopy() throws Exception {
        List<EntityInteractionLockEntry> entries = readInteraction(
                "[{\"id\":\"minecraft:chest_boat\",\"lock_actions\":[\"mount\"],\"nbt\":{\"Type\":\"cherry\"}}]");
        assertEquals(1, entries.size());
        assertEquals("minecraft:cherry_chest_boat", entries.get(0).getId());
        assertEquals(List.of("mount"), entries.get(0).getLockActions());
    }

    @Test
    void otherIdsAreLeftAlone() {
        assertEquals(List.of("minecraft:minecart", "minecraft:oak_boat"),
                ids(readAttack("[\"minecraft:minecart\", \"minecraft:oak_boat\"]")));
    }
}
