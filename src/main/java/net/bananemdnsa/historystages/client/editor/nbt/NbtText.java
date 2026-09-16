package net.bananemdnsa.historystages.client.editor.nbt;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The item's name and lore lines are stored as serialised text components, so a name typed as
 * {@code Excalibur} sits in the tag as {@code {"text":"Excalibur"}}. {@code NbtMatcher} compares
 * strings exactly, which means the editor has to write that form or the criterion never matches.
 */
public final class NbtText {

    private NbtText() {}

    /** What an anvil writes for a plain name. Text that already is a component is kept as typed. */
    public static String wrap(String plain) {
        if (plain == null) return "";
        String trimmed = plain.trim();
        if (trimmed.startsWith("{")) return trimmed;
        JsonObject component = new JsonObject();
        component.addProperty("text", plain);
        return component.toString();
    }

    /**
     * The plain text inside a component that holds nothing but text, or the raw string when it has
     * styling or anything else a single line of text cannot show without losing it.
     */
    public static String unwrap(String stored) {
        if (stored == null) return "";
        try {
            JsonElement parsed = JsonParser.parseString(stored);
            if (parsed.isJsonObject()) {
                JsonObject object = parsed.getAsJsonObject();
                if (object.size() == 1 && object.has("text") && object.get("text").isJsonPrimitive()) {
                    return object.get("text").getAsString();
                }
            }
        } catch (Exception ignored) {
            // not JSON at all — a hand-written file, shown as it stands
        }
        return stored;
    }
}
