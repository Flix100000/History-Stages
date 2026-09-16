package net.bananemdnsa.historystages.client.editor.widget.list;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.bananemdnsa.historystages.client.editor.nbt.ComponentCriterion;
import net.bananemdnsa.historystages.client.editor.nbt.CustomDataCriterion;
import net.bananemdnsa.historystages.client.editor.nbt.EnchantmentListCriterion;
import net.bananemdnsa.historystages.client.editor.nbt.NbtCriteriaCodec;
import net.bananemdnsa.historystages.client.editor.nbt.NbtCriterion;
import net.bananemdnsa.historystages.client.editor.nbt.TextListCriterion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Guards the shape "import item from inventory" produces on 1.20.1: the item's tag converted to
 * JSON as it is, which the codec has to read back into criteria that match that same item.
 *
 * <p>The test works on the model side rather than calling {@code SearchableItemList}: that class
 * needs a loaded client.
 */
class ItemImportCriteriaShapeTest {

    private static final Gson GSON = new Gson();

    @Test
    void anAnvilRenamedRepairedSwordImportsAsProperties() {
        JsonObject imported = GSON.fromJson("""
                {"display":{"Name":"{\\"text\\":\\"Old Blade\\"}"},"RepairCost":3,"Damage":12}
                """, JsonObject.class);

        List<NbtCriterion> loaded = NbtCriteriaCodec.load(imported);

        assertEquals(3, loaded.size());
        for (NbtCriterion criterion : loaded) {
            assertInstanceOf(ComponentCriterion.class, criterion);
        }
        assertEquals(imported, NbtCriteriaCodec.write(loaded),
                "an import that writes back differently no longer matches the item it came from");
    }

    @Test
    void loreImportsAsATextList() {
        JsonObject imported = GSON.fromJson("""
                {"display":{"Lore":["{\\"italic\\":false,\\"text\\":\\"styled\\"}"]}}
                """, JsonObject.class);

        List<NbtCriterion> loaded = NbtCriteriaCodec.load(imported);
        assertInstanceOf(TextListCriterion.class, loaded.get(0));
        assertEquals(imported, NbtCriteriaCodec.write(loaded), "styled lore has to survive untouched");
    }

    @Test
    void enchantmentsStayTopLevel() {
        JsonObject imported = GSON.fromJson("""
                {"Enchantments":[{"id":"minecraft:sharpness","lvl":3}]}
                """, JsonObject.class);

        assertInstanceOf(EnchantmentListCriterion.class, NbtCriteriaCodec.load(imported).get(0));
    }

    @Test
    void aModsOwnKeyImportsAsCustomData() {
        JsonObject imported = GSON.fromJson("""
                {"somemod_charge":40}
                """, JsonObject.class);

        assertInstanceOf(CustomDataCriterion.class, NbtCriteriaCodec.load(imported).get(0));
    }
}
