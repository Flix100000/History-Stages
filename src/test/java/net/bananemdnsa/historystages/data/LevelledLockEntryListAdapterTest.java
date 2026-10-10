package net.bananemdnsa.historystages.data;

import java.lang.reflect.Type;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelledLockEntryListAdapterTest {

    private static final Type LIST = new TypeToken<List<LevelledLockEntry>>() {}.getType();

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LIST, new LevelledLockEntryListAdapter())
            .create();

    @Test
    void aBareStringIsAnAllLevelsEntryThatLocksItems() {
        List<LevelledLockEntry> entries = GSON.fromJson("[\"minecraft:sharpness\"]", LIST);
        assertEquals(1, entries.size());
        assertEquals("minecraft:sharpness", entries.get(0).getId());
        assertNull(entries.get(0).getMinLevel());
        assertTrue(entries.get(0).isLockItems());
    }

    @Test
    void aPlainEntryIsWrittenAsABareString() {
        assertEquals("[\"minecraft:sharpness\"]",
                GSON.toJson(List.of(new LevelledLockEntry("minecraft:sharpness", null, true)), LIST));
    }

    @Test
    void levelAndSwitchSurviveTheRoundTrip() {
        String json = GSON.toJson(List.of(
                new LevelledLockEntry("minecraft:mending", 2, false)), LIST);
        assertTrue(json.contains("\"min_level\":2"), json);
        assertTrue(json.contains("\"lock_items\":false"), json);

        LevelledLockEntry back = ((List<LevelledLockEntry>) GSON.fromJson(json, LIST)).get(0);
        assertEquals("minecraft:mending", back.getId());
        assertEquals(2, back.getMinLevel());
        assertFalse(back.isLockItems());
    }

    /** The default is not written, so a hand-edited file stays short. */
    @Test
    void lockItemsTrueIsNotWritten() {
        String json = GSON.toJson(List.of(new LevelledLockEntry("minecraft:speed", 2, true)), LIST);
        assertFalse(json.contains("lock_items"), json);
    }

    @Test
    void aFreshStageHasNeither() {
        StageEntry stage = new StageEntry();
        assertTrue(stage.getEnchantmentEntries().isEmpty());
        assertTrue(stage.getEffectEntries().isEmpty());
    }

    @Test
    void bothListsSurviveGsonAndListTheirIds() {
        StageEntry stage = new StageEntry();
        stage.setEnchantmentEntries(List.of(new LevelledLockEntry("minecraft:sharpness", 4, false)));
        stage.setEffectEntries(List.of(new LevelledLockEntry("minecraft:speed", null, true)));

        Gson gson = new Gson();
        StageEntry restored = gson.fromJson(gson.toJson(stage), StageEntry.class);

        assertEquals(List.of("minecraft:sharpness"), restored.getAllEnchantmentIds());
        assertEquals(4, restored.getEnchantmentEntries().get(0).getMinLevel());
        assertFalse(restored.getEnchantmentEntries().get(0).isLockItems());
        assertEquals(List.of("minecraft:speed"), restored.getAllEffectIds());
    }

    @Test
    void aStageFileWithoutTheKeysLoadsWithEmptyLists() {
        StageEntry restored = new Gson().fromJson("{\"display_name\":\"Old\"}", StageEntry.class);
        assertTrue(restored.getEnchantmentEntries().isEmpty());
        assertTrue(restored.getEffectEntries().isEmpty());
    }

    /** Unused lists cost nothing: the save packet has a hard size limit. */
    @Test
    void emptyListsAreNotWritten() {
        StageEntry stage = new StageEntry();
        stage.setEnchantmentEntries(List.of());
        String json = new Gson().toJson(stage);
        assertFalse(json.contains("\"enchantments\""), json);
        assertFalse(json.contains("\"effects\""), json);
    }

    @Test
    void copyIsDeep() {
        StageEntry stage = new StageEntry();
        stage.setEnchantmentEntries(List.of(new LevelledLockEntry("minecraft:sharpness", 4, true)));
        stage.setEffectEntries(List.of(new LevelledLockEntry("minecraft:speed", null, true)));

        StageEntry copy = stage.copy();

        assertEquals(4, copy.getEnchantmentEntries().get(0).getMinLevel());
        assertNotSame(stage.getEnchantmentEntries().get(0), copy.getEnchantmentEntries().get(0));
        assertNotSame(stage.getEffectEntries().get(0), copy.getEffectEntries().get(0));
    }
}
