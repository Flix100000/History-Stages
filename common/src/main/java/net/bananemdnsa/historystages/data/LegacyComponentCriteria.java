package net.bananemdnsa.historystages.data;

import java.util.Map;
import java.util.function.BiConsumer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * Rewrites {@code components} criteria written for 1.21.1 into the shape the 26.2 codecs encode.
 *
 * <p>Stage files from 6.1.x keep loading on 26.2, but a component criterion in an old encoding
 * would simply never match, and a lock that never matches is an open lock. Vanilla's DataFixer is
 * no help here: criteria are partial and may hold ranges like {@code "1-4"}, and the tooltip fix
 * would add a {@code tooltip_display} key the item doesn't have. So only the known changes are
 * handled, the rest is reported.
 *
 * <p>No Minecraft types on purpose, so this stays unit-testable.
 */
public final class LegacyComponentCriteria {

    private LegacyComponentCriteria() {}

    /**
     * Returns {@code criteria} with its {@code components} block rewritten, or {@code criteria}
     * itself when nothing in it uses an old shape. The input is never changed.
     *
     * @param problems gets (component id, message) for criteria that can't be read on 26.2
     */
    public static JsonObject normalise(JsonObject criteria, BiConsumer<String, String> problems) {
        if (criteria == null || !criteria.has("components") || !criteria.get("components").isJsonObject()) {
            return criteria;
        }
        JsonObject components = criteria.getAsJsonObject("components");
        JsonObject rewritten = null;
        for (Map.Entry<String, JsonElement> e : components.entrySet()) {
            JsonElement value = convert(path(e.getKey()), e.getValue(), e.getKey(), problems);
            if (value != e.getValue()) {
                if (rewritten == null) rewritten = components.deepCopy();
                rewritten.add(e.getKey(), value);
            }
        }
        if (rewritten == null) return criteria;
        JsonObject out = new JsonObject();
        for (Map.Entry<String, JsonElement> e : criteria.entrySet()) {
            out.add(e.getKey(), e.getKey().equals("components") ? rewritten : e.getValue());
        }
        return out;
    }

    /** Vanilla ids may be written with or without the namespace. */
    private static String path(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    private static JsonElement convert(String path, JsonElement value, String id, BiConsumer<String, String> problems) {
        switch (path) {
            case "hide_tooltip", "hide_additional_tooltip" -> {
                problems.accept(id, "component " + id + " no longer exists since 1.21.5 (now minecraft:tooltip_display); this criterion never matches");
                return value;
            }
            case "custom_name", "item_name" -> {
                return text(value);
            }
            case "lore" -> {
                if (!value.isJsonArray()) return value;
                JsonArray lines = value.getAsJsonArray();
                JsonArray out = null;
                for (int i = 0; i < lines.size(); i++) {
                    JsonElement line = text(lines.get(i));
                    if (line != lines.get(i) && out == null) {
                        out = new JsonArray();
                        for (int j = 0; j < i; j++) out.add(lines.get(j));
                    }
                    if (out != null) out.add(line);
                }
                return out != null ? out : value;
            }
            case "custom_model_data" -> {
                // 1.21.4 turned the single int into lists; the old int became the first float.
                if (!value.isJsonPrimitive()) return value;
                JsonArray floats = new JsonArray();
                floats.add(value);
                JsonObject out = new JsonObject();
                out.add("floats", floats);
                return out;
            }
            default -> {
                // fall through to the object-shaped cases below
            }
        }
        if (!value.isJsonObject()) return value;
        JsonObject obj = value.getAsJsonObject();
        JsonObject out = obj;
        if (obj.has("show_in_tooltip")) {
            // Moved to tooltip_display in 1.21.5. Dropping it matches a little wider than the old
            // criterion did, which beats a criterion that matches nothing.
            out = obj.deepCopy();
            out.remove("show_in_tooltip");
        }
        switch (path) {
            case "enchantments", "stored_enchantments" -> {
                if (out.size() == 1 && out.get("levels") instanceof JsonObject levels) return levels;
            }
            case "dyed_color" -> {
                if (out.size() == 1 && out.get("rgb") instanceof JsonPrimitive rgb) return rgb;
            }
            case "attribute_modifiers" -> {
                if (out.size() == 1 && out.get("modifiers") instanceof JsonArray modifiers) return modifiers;
            }
            case "can_break", "can_place_on" -> {
                if (out.has("predicates")) {
                    problems.accept(id, "component " + id + " uses the 1.21.1 {\"predicates\":[...]} form, which 26.2 no longer encodes; rewrite it in the editor");
                }
            }
            default -> {}
        }
        return out;
    }

    /**
     * 1.21.1 stored text components as a JSON string ({@code "{\"text\":\"Excalibur\"}"} or
     * {@code "\"Excalibur\""}); 26.2 encodes plain text as the bare string and styled text as an
     * object.
     */
    private static JsonElement text(JsonElement value) {
        if (!(value instanceof JsonPrimitive prim) || !prim.isString()) return value;
        String s = prim.getAsString().trim();
        if (s.isEmpty() || (s.charAt(0) != '{' && s.charAt(0) != '"')) return value;
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(s);
        } catch (RuntimeException notJson) {
            return value;
        }
        if (parsed instanceof JsonPrimitive p && p.isString()) return p;
        if (parsed instanceof JsonObject o) {
            if (o.size() == 1 && o.get("text") instanceof JsonPrimitive t && t.isString()) return t;
            return o;
        }
        return value;
    }
}
