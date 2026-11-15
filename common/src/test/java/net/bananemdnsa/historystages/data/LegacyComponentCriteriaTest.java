package net.bananemdnsa.historystages.data;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class LegacyComponentCriteriaTest {

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    private static JsonObject components(String s) {
        return LegacyComponentCriteria.normalise(json("{\"components\":" + s + "}"), (id, msg) -> {})
                .getAsJsonObject("components");
    }

    @Test
    void currentShapesComeBackUntouched() {
        JsonObject criteria = json("{\"components\":{\"minecraft:custom_name\":\"Excalibur\","
                + "\"minecraft:enchantments\":{\"minecraft:sharpness\":\"4-5\"}},\"Enchantments\":[]}");
        assertSame(criteria, LegacyComponentCriteria.normalise(criteria, (id, msg) -> {}));
    }

    @Test
    void enchantmentLevelsAreUnwrappedAndShowInTooltipDropped() {
        assertEquals(json("{\"minecraft:enchantments\":{\"minecraft:sharpness\":5}}"),
                components("{\"minecraft:enchantments\":{\"levels\":{\"minecraft:sharpness\":5},\"show_in_tooltip\":false}}"));
        assertEquals(json("{\"stored_enchantments\":{\"minecraft:mending\":1}}"),
                components("{\"stored_enchantments\":{\"levels\":{\"minecraft:mending\":1}}}"));
    }

    @Test
    void jsonTextNamesBecomePlainOrStyledText() {
        assertEquals(json("{\"minecraft:custom_name\":\"Excalibur\"}"),
                components("{\"minecraft:custom_name\":\"{\\\"text\\\":\\\"Excalibur\\\"}\"}"));
        assertEquals(json("{\"minecraft:item_name\":\"Excalibur\"}"),
                components("{\"minecraft:item_name\":\"\\\"Excalibur\\\"\"}"));
        assertEquals(json("{\"minecraft:custom_name\":{\"text\":\"Excalibur\",\"color\":\"gold\"}}"),
                components("{\"minecraft:custom_name\":\"{\\\"text\\\":\\\"Excalibur\\\",\\\"color\\\":\\\"gold\\\"}\"}"));
        assertEquals(json("{\"minecraft:lore\":[\"plain\",\"Old\"]}"),
                components("{\"minecraft:lore\":[\"plain\",\"{\\\"text\\\":\\\"Old\\\"}\"]}"));
    }

    @Test
    void otherKnownWrappersAreUnwrapped() {
        assertEquals(json("{\"minecraft:dyed_color\":16711680}"),
                components("{\"minecraft:dyed_color\":{\"rgb\":16711680,\"show_in_tooltip\":true}}"));
        assertEquals(json("{\"minecraft:custom_model_data\":{\"floats\":[7]}}"),
                components("{\"minecraft:custom_model_data\":7}"));
        assertEquals(json("{\"minecraft:unbreakable\":{}}"),
                components("{\"minecraft:unbreakable\":{\"show_in_tooltip\":false}}"));
    }

    @Test
    void unreadableShapesAreReported() {
        List<String> problems = new ArrayList<>();
        LegacyComponentCriteria.normalise(json("{\"components\":{\"minecraft:hide_tooltip\":{},"
                + "\"minecraft:can_break\":{\"predicates\":[{\"blocks\":\"minecraft:stone\"}]}}}"),
                (id, msg) -> problems.add(id));
        assertEquals(List.of("minecraft:hide_tooltip", "minecraft:can_break"), problems);
    }

    @Test
    void theInputIsNotChanged() {
        JsonObject criteria = json("{\"components\":{\"minecraft:enchantments\":{\"levels\":{\"minecraft:sharpness\":5}}}}");
        JsonObject before = criteria.deepCopy();
        assertNotSame(criteria, LegacyComponentCriteria.normalise(criteria, (id, msg) -> {}));
        assertEquals(before, criteria);
    }
}
