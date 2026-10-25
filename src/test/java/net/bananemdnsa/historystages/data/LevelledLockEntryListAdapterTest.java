package net.bananemdnsa.historystages.data;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.bananemdnsa.historystages.api.lock.LockActions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelledLockEntryListAdapterTest {

    private static final Type LIST = new TypeToken<List<LevelledLockEntry>>() {}.getType();

    private static final Gson ENCHANTMENTS = new GsonBuilder()
            .registerTypeAdapter(LIST, new LevelledLockEntryListAdapter.Enchantments()).create();
    private static final Gson EFFECTS = new GsonBuilder()
            .registerTypeAdapter(LIST, new LevelledLockEntryListAdapter.Effects()).create();

    private static List<LevelledLockEntry> read(Gson gson, String json) {
        return gson.fromJson(json, LIST);
    }

    @Test
    void aBareStringLocksEveryLevelAndAction() {
        LevelledLockEntry e = read(ENCHANTMENTS, "[\"minecraft:sharpness\"]").get(0);
        assertEquals("minecraft:sharpness", e.getId());
        assertNull(e.getMinLevel());
        assertNull(e.getLockActions());
        assertNull(e.getExcludedItemTypes());
    }

    @Test
    void aPlainEntryIsWrittenAsABareString() {
        assertEquals("[\"minecraft:sharpness\"]",
                ENCHANTMENTS.toJson(List.of(new LevelledLockEntry("minecraft:sharpness")), LIST));
    }

    /** The complement is stored, against the category's own vocabulary, like fluids. */
    @Test
    void narrowedActionsAreStoredAsTheirComplement() {
        String json = EFFECTS.toJson(List.of(new LevelledLockEntry("minecraft:speed", 2,
                List.of("use", "brew"), null)), LIST);
        assertTrue(json.contains("\"min_level\":2"), json);
        assertTrue(json.contains("\"unlock_actions\""), json);
        assertTrue(json.contains("pickup"), json);
        assertFalse(json.contains("anvil"), json);

        LevelledLockEntry back = read(EFFECTS, json).get(0);
        assertEquals(List.of("use", "brew"), back.getLockActions());
        assertEquals(2, back.getMinLevel());
    }

    @Test
    void excludedItemTypesRoundTrip() {
        String json = EFFECTS.toJson(List.of(new LevelledLockEntry("minecraft:speed", null, null,
                List.of("minecraft:tipped_arrow"))), LIST);
        assertTrue(json.contains("\"excluded_item_types\""), json);
        assertEquals(List.of("minecraft:tipped_arrow"), read(EFFECTS, json).get(0).getExcludedItemTypes());
    }

    /** Written by the first cut of this feature, never released, read as "only the stations". */
    @Test
    void lockItemsFalseReadsAsStationsOnly() {
        LevelledLockEntry e = read(ENCHANTMENTS,
                "[{\"id\":\"minecraft:mending\",\"lock_items\":false}]").get(0);
        assertEquals(List.of("enchanting_table", "anvil"), e.getLockActions());
    }

    @Test
    void vocabulariesAreWhatTheEditorOffers() {
        assertEquals(List.of("use", "attack", "equip", "pickup", "trade", "loot", "recipe", "icon",
                "enchanting_table", "anvil"), LockActions.ENCHANTMENT);
        assertEquals(List.of("use", "pickup", "trade", "loot", "recipe", "icon", "brew"), LockActions.EFFECT);
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
        stage.setEnchantmentEntries(List.of(new LevelledLockEntry("minecraft:sharpness", 4,
                List.of("anvil"), null)));
        stage.setEffectEntries(List.of(new LevelledLockEntry("minecraft:speed")));

        Gson gson = new Gson();
        StageEntry restored = gson.fromJson(gson.toJson(stage), StageEntry.class);

        assertEquals(List.of("minecraft:sharpness"), restored.getAllEnchantmentIds());
        assertEquals(4, restored.getEnchantmentEntries().get(0).getMinLevel());
        assertEquals(List.of("anvil"), restored.getEnchantmentEntries().get(0).getLockActions());
        assertEquals(List.of("minecraft:speed"), restored.getAllEffectIds());
    }

    @Test
    void aStageFileWithoutTheKeysLoadsWithEmptyLists() {
        StageEntry restored = new Gson().fromJson("{\"display_name\":\"Old\"}", StageEntry.class);
        assertTrue(restored.getEnchantmentEntries().isEmpty());
        assertTrue(restored.getEffectEntries().isEmpty());
    }

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
        stage.setEnchantmentEntries(List.of(new LevelledLockEntry("minecraft:sharpness", 4, null, null)));
        stage.setEffectEntries(List.of(new LevelledLockEntry("minecraft:speed")));

        StageEntry copy = stage.copy();

        assertEquals(4, copy.getEnchantmentEntries().get(0).getMinLevel());
        assertNotSame(stage.getEnchantmentEntries().get(0), copy.getEnchantmentEntries().get(0));
        assertNotSame(stage.getEffectEntries().get(0), copy.getEffectEntries().get(0));
    }

    /** Fixed actions reach these entries the way they reach items and fluids. */
    @Test
    void fixedActionsApplyToBothLists() {
        StageEntry stage = new StageEntry();
        stage.setFixedEnchantmentLockActions(Map.of("anvil", false));
        stage.setFixedEffectLockActions(Map.of("brew", false));

        assertFalse(stage.effectiveEnchantmentLockActions(null).contains("anvil"));
        assertTrue(stage.effectiveEnchantmentLockActions(null).contains("enchanting_table"));
        assertFalse(stage.effectiveEffectLockActions(null).contains("brew"));

        Gson gson = new Gson();
        StageEntry restored = gson.fromJson(gson.toJson(stage), StageEntry.class);
        assertEquals(Map.of("anvil", false), restored.getFixedEnchantmentLockActions());
    }
}
