package net.bananemdnsa.historystages.client.editor.nbt;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;

/**
 * Translates between the stage file's NBT criteria object and the flat list of criteria the editor
 * edits.
 *
 * <p>On 1.20.1 {@code NbtMatcher} compares the criteria against the item's tag as it is, so the
 * split here is only about how a value is edited: the two enchantment lists, the known properties
 * from {@link NbtPresets} (with {@code display.Name} and {@code display.Lore} read out of the
 * {@code display} compound), and every other top-level key as free custom data.
 */
public final class NbtCriteriaCodec {

    private static final String ENCHANTMENTS = "Enchantments";
    private static final String STORED_ENCHANTMENTS = "StoredEnchantments";
    private static final String DISPLAY = "display";

    private NbtCriteriaCodec() {}

    public static List<NbtCriterion> load(JsonObject nbt) {
        List<NbtCriterion> out = new ArrayList<>();
        if (nbt == null) return out;

        for (var entry : nbt.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if ((ENCHANTMENTS.equals(key) || STORED_ENCHANTMENTS.equals(key)) && value.isJsonArray()) {
                out.add(loadEnchantments(key, value.getAsJsonArray()));
            } else if (DISPLAY.equals(key) && value.isJsonObject()) {
                for (var sub : value.getAsJsonObject().entrySet()) {
                    out.add(loadProperty(DISPLAY + "." + sub.getKey(), sub.getValue()));
                }
            } else if (NbtPresets.isKnownTopLevel(key)) {
                out.add(loadProperty(key, value));
            } else {
                out.add(new CustomDataCriterion(key, asText(value), false));
            }
        }
        return out;
    }

    public static JsonObject write(List<NbtCriterion> criteria) {
        JsonObject out = new JsonObject();

        for (NbtCriterion criterion : criteria) {
            if (criterion.isEmpty()) continue;

            if (criterion instanceof EnchantmentListCriterion ench) {
                JsonArray array = new JsonArray();
                for (EnchantmentListCriterion.Line line : ench.lines) {
                    if (line.id.isBlank()) continue;
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", line.id);
                    if (line.level.matches("\\d+-\\d+")) {
                        obj.addProperty("lvl", line.level);
                    } else {
                        try {
                            obj.addProperty("lvl", Integer.parseInt(line.level.trim()));
                        } catch (NumberFormatException e) {
                            obj.addProperty("lvl", 1);
                        }
                    }
                    array.add(obj);
                }
                if (!array.isEmpty()) out.add(ench.key(), array);
            } else if (criterion instanceof TextListCriterion lore) {
                JsonArray array = new JsonArray();
                for (String line : lore.lines) {
                    if (!line.isBlank()) array.add(NbtText.wrap(line));
                }
                if (!array.isEmpty()) putAtPath(out, lore.componentId(), array);
            } else if (criterion instanceof ComponentCriterion comp) {
                JsonElement parsed = parseOrNull(comp.valueJson);
                if (parsed != null) putAtPath(out, comp.componentId(), parsed);
            } else if (criterion instanceof CustomDataCriterion custom) {
                out.add(custom.key, typedValue(custom.valueText));
            }
        }
        return out;
    }

    /** The value stored at a dotted path, or null when some part of the path is missing. */
    public static JsonElement valueAtPath(JsonObject nbt, String path) {
        if (nbt == null || path == null) return null;
        JsonElement current = nbt;
        for (String part : path.split("\\.")) {
            if (!current.isJsonObject() || !current.getAsJsonObject().has(part)) return null;
            current = current.getAsJsonObject().get(part);
        }
        return current;
    }

    private static void putAtPath(JsonObject root, String path, JsonElement value) {
        String[] parts = path.split("\\.");
        JsonObject parent = root;
        for (int i = 0; i < parts.length - 1; i++) {
            JsonElement child = parent.get(parts[i]);
            if (child == null || !child.isJsonObject()) {
                child = new JsonObject();
                parent.add(parts[i], child);
            }
            parent = child.getAsJsonObject();
        }
        parent.add(parts[parts.length - 1], value);
    }

    private static NbtCriterion loadProperty(String path, JsonElement value) {
        NbtPresets.Preset preset = NbtPresets.byComponentId(path);
        String presetName = preset == null ? null : preset.nameKey();

        if (NbtPresets.LORE_COMPONENT.equals(path) && value.isJsonArray()) {
            TextListCriterion lore = new TextListCriterion(path, presetName);
            for (JsonElement line : value.getAsJsonArray()) {
                lore.lines.add(NbtText.unwrap(asText(line)));
            }
            return lore;
        }
        return new ComponentCriterion(path, value.toString(), presetName);
    }

    private static EnchantmentListCriterion loadEnchantments(String key, JsonArray array) {
        EnchantmentListCriterion criterion = new EnchantmentListCriterion(key);
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject obj = element.getAsJsonObject();
            String id = obj.has("id") ? obj.get("id").getAsString() : "";
            String lvl = obj.has("lvl") ? obj.get("lvl").getAsString() : "";
            criterion.lines.add(new EnchantmentListCriterion.Line(id, lvl));
        }
        return criterion;
    }

    /** Primitives come back bare so the editor shows {@code main_01}, not {@code "main_01"}. */
    private static String asText(JsonElement element) {
        if (element == null || element.isJsonNull()) return "";
        return element.isJsonPrimitive() ? element.getAsString() : element.toString();
    }

    /**
     * Keeps the JSON type of a hand-typed custom value. Writing {@code 5} back as the string "5"
     * would make it unmatchable: {@code NbtMatcher} only compares a string criterion against a
     * {@code StringTag}, or against a number when it reads as a "1-4" range.
     */
    private static JsonElement typedValue(String text) {
        String trimmed = text.trim();
        try {
            JsonElement parsed = JsonParser.parseString(trimmed);
            if (parsed.isJsonPrimitive() || parsed.isJsonObject() || parsed.isJsonArray()) {
                return parsed;
            }
        } catch (Exception ignored) {
            // not JSON — a plain string is exactly what was meant
        }
        return new JsonPrimitive(trimmed);
    }

    /** Property values may be any JSON type. */
    private static JsonElement parseOrNull(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) return null;
        try {
            JsonElement parsed = JsonParser.parseString(trimmed);
            return parsed.isJsonNull() ? null : parsed;
        } catch (Exception e) {
            return null;
        }
    }
}
