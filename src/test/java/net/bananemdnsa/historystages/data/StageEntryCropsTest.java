package net.bananemdnsa.historystages.data;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageEntryCropsTest {

    private static final Gson GSON = new Gson();

    @Test
    void flatArrayLoads() {
        StageEntry stage = GSON.fromJson(
                "{\"crops\":[\"minecraft:wheat\",\"#minecraft:saplings\"]}", StageEntry.class);
        assertEquals(List.of("minecraft:wheat", "#minecraft:saplings"), stage.getCrops());
        assertTrue(stage.getCropModLinked().isEmpty());
    }

    @Test
    void objectFormRoundTrips() {
        StageEntry stage = new StageEntry();
        stage.setCrops(List.of("croptopia:tomato", "minecraft:wheat"));
        stage.setCropModLinked(List.of("croptopia:tomato"));

        StageEntry reloaded = GSON.fromJson(stage.toJson(), StageEntry.class);
        assertEquals(List.of("croptopia:tomato", "minecraft:wheat"), reloaded.getCrops());
        assertEquals(List.of("croptopia:tomato"), reloaded.getCropModLinked());
    }

    @Test
    void emptyStageHasNoCrops() {
        assertNotNull(new StageEntry().getCrops());
        assertTrue(new StageEntry().getCrops().isEmpty());
    }

    /** Most stages never use plants; their files should not grow an empty "crops" block. */
    @Test
    void emptyCropsAreNotWritten() {
        StageEntry stage = new StageEntry();
        stage.setCrops(List.of());
        assertFalse(JsonParser.parseString(stage.toJson()).getAsJsonObject().has("crops"));
    }

    /** copy() names every field by hand, and the editor's save path starts from a copy. */
    @Test
    void copyKeepsCrops() {
        StageEntry original = new StageEntry();
        original.setCrops(List.of("minecraft:wheat"));
        original.setCropModLinked(List.of("minecraft:wheat"));

        StageEntry copy = original.copy();
        assertEquals(List.of("minecraft:wheat"), copy.getCrops());
        assertEquals(List.of("minecraft:wheat"), copy.getCropModLinked());

        original.setCrops(List.of());
        assertEquals(List.of("minecraft:wheat"), copy.getCrops());
    }
}
