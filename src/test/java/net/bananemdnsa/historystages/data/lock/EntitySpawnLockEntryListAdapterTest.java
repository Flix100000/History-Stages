package net.bananemdnsa.historystages.data.lock;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.bananemdnsa.historystages.data.lock.spawn.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EntitySpawnLockEntryListAdapterTest {

    private static final Type LIST = new TypeToken<List<EntitySpawnLockEntry>>() {}.getType();
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LIST, new EntitySpawnLockEntryListAdapter()).create();

    private static List<EntitySpawnLockEntry> read(String json) {
        return GSON.fromJson(json, LIST);
    }

    private static void assertSameJson(String expected, List<EntitySpawnLockEntry> entries) {
        assertEquals(JsonParser.parseString(expected), JsonParser.parseString(GSON.toJson(entries, LIST)));
    }

    @Test
    void legacyFilesStayByteIdentical() {
        // Tree comparison (assertSameJson) ignores key order, so it can't catch a reordered field —
        // this one compares the actual written string.
        String json = "[\"minecraft:creeper\","
                + "{\"id\":\"minecraft:zombie\",\"unlock_sources\":[\"spawn_egg\"]},"
                + "{\"id\":\"minecraft:skeleton\",\"unlock_dimensions\":[\"minecraft:the_nether\"]}]";
        assertEquals(json, GSON.toJson(read(json), LIST));
    }

    @Test
    void legacyLockSourcesKeyIsStillRead() {
        EntitySpawnLockEntry entry = read("[{\"id\":\"a:b\",\"lock_sources\":[\"natural\"]}]").get(0);
        assertEquals(List.of("natural"), entry.getLockSources());
    }

    @Test
    void aFullRuleRoundTrips() {
        String json = """
                [{"id":"minecraft:zombie","phase":"after_unlock",
                  "unlock_sources":["structure","breeding","summon","spawn_egg"],
                  "conditions":{
                    "dimensions":{"mode":"only","ids":["minecraft:overworld"]},
                    "biomes":{"mode":"exclude","ids":["#minecraft:is_forest","minecraft:swamp"]},
                    "sky":"hidden","y":{"min":-64,"max":40},"time":"night",
                    "light":{"min":0,"max":7},"weather":"rain","moon_phases":[0,4]},
                  "extra_biomes":{"ids":["minecraft:mushroom_fields"],"weight":95,"min_group":4,"max_group":4,
                    "ignore_spawn_rules":true}}]""";

        EntitySpawnLockEntry entry = read(json).get(0);
        assertEquals(GenerationPhase.AFTER_UNLOCK, entry.getPhase());
        assertEquals(8, entry.getConditions().count());
        assertEquals(new SpawnWeight(95, 4, 4), entry.getExtraBiomes().weight());
        assertTrue(entry.getExtraBiomes().ignoreSpawnRules());
        assertSameJson(json, List.of(entry));
    }

    @Test
    void aDimensionFilterNextToNewConditionsIsWrittenUnderConditions() {
        EntitySpawnLockEntry entry = new EntitySpawnLockEntry("a:b", null, GenerationPhase.WHILE_LOCKED,
                new SpawnConditions(IdFilter.only(List.of("minecraft:overworld")), null, null, null,
                        TimeOfDay.DAY, null, null, Set.of()), null);
        assertSameJson("""
                [{"id":"a:b","conditions":{"dimensions":{"mode":"only","ids":["minecraft:overworld"]},"time":"day"}}]""",
                List.of(entry));
    }

    @Test
    void extraBiomesWithoutWeightOmitTheNumbers() {
        EntitySpawnLockEntry entry = new EntitySpawnLockEntry("a:b", null, GenerationPhase.WHILE_LOCKED, null,
                new ExtraBiomeSpawns(List.of("minecraft:desert"), null, false));
        assertSameJson("[{\"id\":\"a:b\",\"extra_biomes\":{\"ids\":[\"minecraft:desert\"]}}]", List.of(entry));
    }

    @Test
    void unknownConditionValuesAreDroppedNotFatal() {
        EntitySpawnLockEntry entry = read("""
                [{"id":"a:b","conditions":{"time":"dusk","sky":42,"biomes":{"mode":"maybe","ids":["x:y"]},
                  "y":{"min":"low"},"moon_phases":["full",2]}}]""").get(0);
        assertEquals(Set.of(2), entry.getConditions().moonPhases());
        assertEquals(1, entry.getConditions().count());
        // one dropped value each for time, sky, biomes, y, and the "full" moon phase entry
        assertEquals(5, entry.getReadProblems().size());
    }

    @Test
    void aNonIntegerRangeBoundIsDropped() {
        EntitySpawnLockEntry entry = read(
                "[{\"id\":\"a:b\",\"conditions\":{\"y\":{\"min\":1.5,\"max\":40}}}]").get(0);
        assertNull(entry.getConditions().height());
        assertEquals(1, entry.getReadProblems().size());
    }

    @Test
    void extraBiomesWeightWithoutTheGroupSizeIsDropped() {
        EntitySpawnLockEntry entry = read(
                "[{\"id\":\"a:b\",\"extra_biomes\":{\"ids\":[\"minecraft:desert\"],\"weight\":95}}]").get(0);
        assertNull(entry.getExtraBiomes().weight());
        assertEquals(1, entry.getReadProblems().size());
    }
}
