package net.bananemdnsa.historystages.data.dependency;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.NbtFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pedestal item requirements with several NBT variants, through the reflective stage Gson. */
class DependencyItemNbtVariantsTest {

    private static final Gson GSON = new Gson();

    private static JsonElement itemsOf(String groupJson) {
        DependencyGroup group = GSON.fromJson(groupJson, DependencyGroup.class);
        return JsonParser.parseString(GSON.toJson(group)).getAsJsonObject().get("items");
    }

    @Test
    void twoVariantsStayAnArray() {
        String items = "[{\"id\":\"minecraft:paper\",\"count\":2,\"nbt\":[{\"Material\":\"cobalt\"},{\"Material\":\"manyullyn\"}]}]";
        assertEquals(JsonParser.parseString(items), itemsOf("{\"items\":" + items + "}"));
        DependencyGroup group = GSON.fromJson("{\"items\":" + items + "}", DependencyGroup.class);
        assertEquals(2, group.getItems().get(0).getNbtFilter().alternatives().size());
    }

    @Test
    void oneVariantStaysAnObjectAndNoneStaysAbsent() {
        String single = "[{\"id\":\"minecraft:paper\",\"count\":1,\"nbt\":{\"Material\":\"cobalt\"}}]";
        assertEquals(JsonParser.parseString(single), itemsOf("{\"items\":" + single + "}"));
        String plain = "[{\"id\":\"minecraft:paper\",\"count\":1}]";
        assertEquals(JsonParser.parseString(plain), itemsOf("{\"items\":" + plain + "}"));
    }

    @Test
    void aMissingFieldReadsAsNoFilter() {
        DependencyGroup group = GSON.fromJson("{\"items\":[{\"id\":\"minecraft:paper\",\"count\":1}]}", DependencyGroup.class);
        DependencyItem item = group.getItems().get(0);
        assertFalse(item.hasNbt());
        assertTrue(item.getNbtFilter().isEmpty());
    }

    /** Same for a pedestal requirement: a string nbt must not turn into "any paper counts". */
    @Test
    void aStringNbtOnARequirementStillFailsLoudly() {
        assertThrows(com.google.gson.JsonParseException.class, () -> GSON.fromJson(
                "{\"items\":[{\"id\":\"minecraft:paper\",\"count\":5,\"nbt\":\"{Material:cobalt}\"}]}",
                DependencyGroup.class));
    }

    /** Addon code written before variants passes a null literal for the NBT; that must still compile. */
    @Test
    void aNullLiteralNbtStillMeansNoFilter() {
        DependencyItem item = new DependencyItem("minecraft:paper", 1, null);
        assertFalse(item.hasNbt());
    }

    @Test
    void withFilterKeepsEveryVariant() {
        NbtFilter two = NbtFilter.fromJson(JsonParser.parseString("[{\"m\":\"a\"},{\"m\":\"b\"}]"));
        assertEquals(two, DependencyItem.withFilter("minecraft:paper", 2, two).copy().getNbtFilter());
    }
}
