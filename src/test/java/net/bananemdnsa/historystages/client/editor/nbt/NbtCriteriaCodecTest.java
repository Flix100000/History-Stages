package net.bananemdnsa.historystages.client.editor.nbt;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NbtCriteriaCodecTest {

    private static final Gson GSON = new Gson();

    private static JsonObject json(String raw) {
        return GSON.fromJson(raw, JsonObject.class);
    }

    @Test
    void nothingLoadsToNoCriteria() {
        assertTrue(NbtCriteriaCodec.load(null).isEmpty());
        assertTrue(NbtCriteriaCodec.load(new JsonObject()).isEmpty());
    }

    @Test
    void enchantmentsLoadAsTheirOwnCriterion() {
        List<NbtCriterion> loaded = NbtCriteriaCodec.load(json("""
                {"Enchantments":[{"id":"minecraft:sharpness","lvl":5},
                                 {"id":"minecraft:looting","lvl":"1-3"}]}
                """));

        assertEquals(1, loaded.size());
        EnchantmentListCriterion ench = assertInstanceOf(EnchantmentListCriterion.class, loaded.get(0));
        assertEquals("Enchantments", ench.key());
        assertEquals(2, ench.lines.size());
        assertEquals("minecraft:sharpness", ench.lines.get(0).id);
        assertEquals("5", ench.lines.get(0).level);
        assertEquals("1-3", ench.lines.get(1).level);
    }

    @Test
    void theDisplayNameLoadsAsAPropertyAtItsPath() {
        List<NbtCriterion> loaded = NbtCriteriaCodec.load(json("""
                {"display":{"Name":"{\\"text\\":\\"Excalibur\\"}"}}
                """));

        assertEquals(1, loaded.size());
        ComponentCriterion name = assertInstanceOf(ComponentCriterion.class, loaded.get(0));
        assertEquals("display.Name", name.componentId());
        assertEquals("display.Name", name.identity());
        assertEquals("Excalibur", name.displayValue(), "the field shows the text, not the component");
    }

    @Test
    void loreLoadsAsATextListWithoutItsComponentWrapping() {
        List<NbtCriterion> loaded = NbtCriteriaCodec.load(json("""
                {"display":{"Lore":["{\\"text\\":\\"line one\\"}","{\\"text\\":\\"line two\\"}"]}}
                """));

        TextListCriterion lore = assertInstanceOf(TextListCriterion.class, loaded.get(0));
        assertEquals(List.of("line one", "line two"), lore.lines);
    }

    /** On 1.20.1 these keys are the item's own data, not leftovers in a custom_data tag. */
    @Test
    void aKnownTopLevelKeyLoadsAsAProperty() {
        List<NbtCriterion> loaded = NbtCriteriaCodec.load(json("""
                {"Unbreakable":true}
                """));

        ComponentCriterion comp = assertInstanceOf(ComponentCriterion.class, loaded.get(0));
        assertEquals("Unbreakable", comp.componentId());
        assertEquals(ValueKind.PRESENCE, comp.valueKind);
        assertFalse(comp.isEmpty(), "presence is the whole criterion — it must survive the save");
    }

    @Test
    void anUnknownTopLevelKeyLoadsAsCustomData() {
        List<NbtCriterion> loaded = NbtCriteriaCodec.load(json("""
                {"quest":"main_01"}
                """));

        CustomDataCriterion custom = assertInstanceOf(CustomDataCriterion.class, loaded.get(0));
        assertEquals("quest", custom.key);
        assertEquals("main_01", custom.valueText);
        assertFalse(custom.legacySuspect, "nothing is legacy on 1.20.1");
    }

    @Test
    void writingUndoesLoading() {
        String raw = """
                {"Enchantments":[{"id":"minecraft:sharpness","lvl":5}],
                 "display":{"Name":"{\\"text\\":\\"Excalibur\\"}","Lore":["{\\"text\\":\\"a line\\"}"]},
                 "Unbreakable":true,
                 "quest":"main_01",
                 "tier":3}
                """;

        JsonObject written = NbtCriteriaCodec.write(NbtCriteriaCodec.load(json(raw)));

        assertEquals(json(raw), written);
    }

    @Test
    void aTypedNameIsWrittenAsTheComponentAnAnvilStores() {
        ComponentCriterion name = new ComponentCriterion("display.Name", "", null);
        name.setFromDisplay("Excalibur");

        JsonObject written = NbtCriteriaCodec.write(List.of(name));
        assertEquals("{\"text\":\"Excalibur\"}",
                written.getAsJsonObject("display").get("Name").getAsString(),
                "NbtMatcher compares the stored string exactly");
    }

    @Test
    void typedLoreLinesAreWrittenAsComponents() {
        TextListCriterion lore = new TextListCriterion("display.Lore", null);
        lore.lines.add("first");

        JsonObject written = NbtCriteriaCodec.write(List.of(lore));
        assertEquals("{\"text\":\"first\"}",
                written.getAsJsonObject("display").getAsJsonArray("Lore").get(0).getAsString());
    }

    @Test
    void aPotionIdIsPlainText() {
        ComponentCriterion potion = new ComponentCriterion("Potion", "", null);
        potion.setFromDisplay("minecraft:strong_healing");

        JsonObject written = NbtCriteriaCodec.write(List.of(potion));
        assertEquals("minecraft:strong_healing", written.get("Potion").getAsString(),
                "only the display properties are text components");
    }

    @Test
    void aNumericCustomValueStaysNumeric() {
        JsonObject written = NbtCriteriaCodec.write(
                List.of(new CustomDataCriterion("level", "5", false)));

        assertTrue(written.get("level").getAsJsonPrimitive().isNumber(),
                "a number written back as a string can never match a NumericTag");
        assertEquals(5, written.get("level").getAsInt());
    }

    @Test
    void aRangeStaysAString() {
        JsonObject written = NbtCriteriaCodec.write(
                List.of(new CustomDataCriterion("level", "1-4", false)));

        assertTrue(written.get("level").getAsJsonPrimitive().isString());
        assertEquals("1-4", written.get("level").getAsString());
    }

    @Test
    void emptyCriteriaAreDropped() {
        JsonObject written = NbtCriteriaCodec.write(List.of(
                new ComponentCriterion("display.Name", "  ", null),
                new CustomDataCriterion("", "x", false),
                new EnchantmentListCriterion("Enchantments")));

        assertEquals(0, written.size());
    }

    @Test
    void aNumberPropertyStaysANumber() {
        ComponentCriterion cost = new ComponentCriterion("RepairCost", "", null);
        cost.setFromDisplay("3");

        JsonObject written = NbtCriteriaCodec.write(List.of(cost));
        assertTrue(written.get("RepairCost").getAsJsonPrimitive().isNumber());
    }

    @Test
    void aNumberPropertyKeepsARangeAsAString() {
        ComponentCriterion cost = new ComponentCriterion("RepairCost", "", null);
        cost.setFromDisplay("1-4");

        JsonObject written = NbtCriteriaCodec.write(List.of(cost));
        assertEquals("1-4", written.get("RepairCost").getAsString());
    }

    @Test
    void nonsenseInANumberFieldIsRejectedRatherThanStored() {
        ComponentCriterion cost = new ComponentCriterion("RepairCost", "", null);
        cost.setFromDisplay("drei");

        assertTrue(cost.isEmpty(), "a value the matcher could never use must not reach the file");
    }

    @Test
    void anUnknownDisplayEntryStaysRawJson() {
        List<NbtCriterion> loaded = NbtCriteriaCodec.load(json("""
                {"display":{"color":16711680}}
                """));

        ComponentCriterion comp = assertInstanceOf(ComponentCriterion.class, loaded.get(0));
        assertEquals("display.color", comp.componentId());
        assertEquals(ValueKind.JSON, comp.valueKind);
    }

    @Test
    void anUnparseablePropertyValueIsDropped() {
        JsonObject written = NbtCriteriaCodec.write(
                List.of(new ComponentCriterion("BlockEntityTag", "{not json", null)));

        assertEquals(0, written.size());
    }

    @Test
    void aValueIsFoundByItsPath() {
        JsonObject nbt = json("""
                {"display":{"Name":"x"},"RepairCost":3}
                """);

        assertEquals("x", NbtCriteriaCodec.valueAtPath(nbt, "display.Name").getAsString());
        assertEquals(3, NbtCriteriaCodec.valueAtPath(nbt, "RepairCost").getAsInt());
        assertEquals(null, NbtCriteriaCodec.valueAtPath(nbt, "display.Lore"));
    }
}
