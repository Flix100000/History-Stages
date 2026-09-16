package net.bananemdnsa.historystages.client.editor.nbt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NbtPresetsTest {

    @Test
    void everyPresetPointsAtAKnownProperty() {
        assertFalse(NbtPresets.common().isEmpty(), "the 'common' tab would be empty");
        for (NbtPresets.Preset preset : NbtPresets.common()) {
            assertTrue(NbtPresets.knownProperties().contains(preset.componentId()),
                    preset.nameKey() + " points at a path the property tab does not list: "
                            + preset.componentId());
            assertFalse(preset.componentId().contains(":"),
                    preset.componentId() + " is a 1.21 component id, not an NBT path");
            assertNotNull(preset.nameKey());
            assertNotNull(preset.descriptionKey());
        }
    }

    /** A known top-level key must not also be a nested path, or the codec reads it twice. */
    @Test
    void onlyUnnestedKeysCountAsKnownTopLevel() {
        assertTrue(NbtPresets.isKnownTopLevel("RepairCost"));
        assertFalse(NbtPresets.isKnownTopLevel("display.Name"));
        assertFalse(NbtPresets.isKnownTopLevel("quest"));
    }

    @Test
    void nothingIsLegacyOnThisVersion() {
        assertFalse(NbtPresets.isLegacyKey("Unbreakable"));
        assertFalse(NbtPresets.isLegacyKey("display"));
        assertNull(NbtPresets.componentForLegacyKey("Unbreakable"));
    }

    /**
     * The two enchantment lists mean different things — one is what an item is enchanted with, the
     * other what a book is carrying. They shared a description once and were indistinguishable in
     * the editor because of it.
     */
    @Test
    void theTwoEnchantmentListsNeverShareWording() {
        assertNotEquals(NbtPresets.enchantmentNameKey(NbtPresets.ENCHANTMENTS),
                NbtPresets.enchantmentNameKey(NbtPresets.STORED_ENCHANTMENTS));
        assertNotEquals(NbtPresets.enchantmentDescriptionKey(NbtPresets.ENCHANTMENTS),
                NbtPresets.enchantmentDescriptionKey(NbtPresets.STORED_ENCHANTMENTS));
    }
}
