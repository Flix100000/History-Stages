package net.bananemdnsa.historystages.data.lock;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.category.LockCategories;
import net.bananemdnsa.historystages.data.lock.engine.LockSubjects;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/** NBT filters on attack and interaction entries: storage, the id view, and gating. */
class EntityNbtFilterTest {

    private static final Type ATTACK = new TypeToken<List<EntityAttackLockEntry>>() {}.getType();
    private static final Type INTERACT = new TypeToken<List<EntityInteractionLockEntry>>() {}.getType();
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(ATTACK, new EntityAttackLockEntryListAdapter())
            .registerTypeAdapter(INTERACT, new EntityInteractionLockEntryListAdapter())
            .create();

    private static JsonObject obj(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    /** Stands in for an entity whose saved data has exactly this one string field. */
    private static Predicate<JsonObject> entityWith(String key, String value) {
        return criteria -> criteria.entrySet().stream().allMatch(e ->
                e.getKey().equals(key) && e.getValue().getAsString().equals(value));
    }

    // ---- JSON ----------------------------------------------------------------------

    @Test
    void plainAttackEntriesStayPlainStrings() {
        String json = "[\"minecraft:zombie\",\"minecraft:creeper\"]";
        assertEquals(json, GSON.toJson(GSON.fromJson(json, ATTACK), ATTACK));
    }

    @Test
    void anAttackFilterRoundTripsAsObjectOrArray() {
        String single = "[{\"id\":\"minecraft:villager\",\"nbt\":{\"Age\":\"0-0\"}}]";
        assertEquals(JsonParser.parseString(single),
                JsonParser.parseString(GSON.toJson(GSON.fromJson(single, ATTACK), ATTACK)));

        String several = "[{\"id\":\"minecraft:boat\",\"nbt\":[{\"Type\":\"oak\"},{\"Type\":\"birch\"}]}]";
        List<EntityAttackLockEntry> read = GSON.fromJson(several, ATTACK);
        assertEquals(2, read.get(0).getNbt().alternatives().size());
        assertEquals(JsonParser.parseString(several), JsonParser.parseString(GSON.toJson(read, ATTACK)));
    }

    @Test
    void anInteractionFilterIsWrittenBesideTheOtherFields() {
        String json = "[{\"id\":\"minecraft:boat\",\"unlock_actions\":[\"other\"],\"nbt\":{\"Type\":\"bamboo\"}}]";
        List<EntityInteractionLockEntry> read = GSON.fromJson(json, INTERACT);
        assertTrue(read.get(0).hasNbt());
        assertEquals(JsonParser.parseString(json), JsonParser.parseString(GSON.toJson(read, INTERACT)));
    }

    @Test
    void anInteractionEntryWithOnlyAFilterIsNoLongerAPlainString() {
        List<EntityInteractionLockEntry> entries = List.of(new EntityInteractionLockEntry(
                "minecraft:boat", null, null, EntityNbtFilter.of(List.of(obj("{\"Type\":\"oak\"}")))));
        assertEquals(JsonParser.parseString("[{\"id\":\"minecraft:boat\",\"nbt\":{\"Type\":\"oak\"}}]"),
                JsonParser.parseString(GSON.toJson(entries, INTERACT)));
    }

    @Test
    void emptyCriteriaAreDroppedBecauseTheyWouldMatchEverything() {
        assertTrue(EntityNbtFilter.fromJson(JsonParser.parseString("[{}]")).isEmpty());
    }

    // ---- the id view -----------------------------------------------------------------

    @Test
    void writingIdsKeepsTheFiltersOfIdsThatStay() {
        EntityLocks locks = new EntityLocks();
        locks.setAttacklockEntries(List.of(
                new EntityAttackLockEntry("minecraft:boat", EntityNbtFilter.of(List.of(obj("{\"Type\":\"oak\"}")))),
                new EntityAttackLockEntry("minecraft:zombie")));

        locks.setAttacklock(List.of("minecraft:boat", "minecraft:husk"));

        List<EntityAttackLockEntry> entries = locks.getAttacklockEntries();
        assertEquals(2, entries.size());
        assertTrue(entries.get(0).hasNbt(), "an id-only write must not wipe the boat's filter");
        assertFalse(entries.get(1).hasNbt());
    }

    @Test
    void removingFromTheIdViewRemovesTheEntry() {
        EntityLocks locks = new EntityLocks();
        locks.setAttacklock(List.of("minecraft:zombie", "bad id"));
        locks.getAttacklock().removeIf(id -> id.contains(" "));
        assertEquals(List.of("minecraft:zombie"), new ArrayList<>(locks.getAttacklock()));
        assertEquals(1, locks.getAttacklockEntries().size());
    }

    @Test
    void aStageCopyKeepsAttackFilters() {
        StageEntry stage = new StageEntry();
        stage.getEntities().setAttacklockEntries(List.of(new EntityAttackLockEntry("minecraft:villager",
                EntityNbtFilter.of(List.of(obj("{\"Age\":\"0-0\"}"))))));
        assertTrue(stage.copy().getEntities().getAttacklockEntries().get(0).hasNbt());
    }

    // ---- gating ----------------------------------------------------------------------

    private static StageEntry lockingOakBoats() {
        StageEntry stage = new StageEntry();
        stage.getEntities().setAttacklockEntries(List.of(new EntityAttackLockEntry("minecraft:boat",
                EntityNbtFilter.of(List.of(obj("{\"Type\":\"oak\"}"), obj("{\"Type\":\"birch\"}"))))));
        return stage;
    }

    @Test
    void aFilteredAttackLockGatesOnlyMatchingEntities() {
        var attack = LockCategories.byId("historystages:attacklock");
        StageEntry stage = lockingOakBoats();
        assertTrue(attack.gates(stage, new LockSubjects.AttackSubject("minecraft:boat", entityWith("Type", "birch"))));
        assertFalse(attack.gates(stage, new LockSubjects.AttackSubject("minecraft:boat", entityWith("Type", "spruce"))));
    }

    @Test
    void aFilteredAttackLockCannotBeConfirmedFromTheIdAlone() {
        var attack = LockCategories.byId("historystages:attacklock");
        assertFalse(attack.gates(lockingOakBoats(), "minecraft:boat"));
        assertFalse(attack.gates(lockingOakBoats(), new LockSubjects.AttackSubject("minecraft:boat", null)));
    }

    @Test
    void anUnfilteredAttackLockStillGatesEveryEntityOfItsType() {
        var attack = LockCategories.byId("historystages:attacklock");
        StageEntry stage = new StageEntry();
        stage.getEntities().setAttacklock(List.of("minecraft:zombie"));
        assertTrue(attack.gates(stage, "minecraft:zombie"));
        assertTrue(attack.gates(stage, new LockSubjects.AttackSubject("minecraft:zombie", entityWith("x", "y"))));
    }
}
