package net.bananemdnsa.historystages.data.auto;

import com.google.gson.*;
import net.bananemdnsa.historystages.api.trigger.TriggerCondition;
import net.bananemdnsa.historystages.data.auto.conditions.UnknownTrigger;

import java.util.ArrayList;
import java.util.List;

/** Shared by the auto-trigger and lock-trigger adapters, so both lists read and write the same way. */
public final class AutoTriggerCodec {

    private AutoTriggerCodec() {}

    public static String readMode(JsonObject obj) {
        return obj.has("mode") && !obj.get("mode").isJsonNull() ? obj.get("mode").getAsString() : null;
    }

    /** @param problems collects values that were read leniently; see {@link AutoTrigger#getReadProblems()} */
    public static List<TriggerCondition> readTriggers(JsonObject obj, JsonDeserializationContext ctx,
                                                      List<String> problems) {
        List<TriggerCondition> triggers = new ArrayList<>();
        if (obj.has("triggers") && obj.get("triggers").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("triggers")) {
                TriggerCondition t = deserializeTrigger(el, ctx, problems);
                if (t != null) triggers.add(t);
            }
        }
        return triggers;
    }

    /**
     * Only a real JSON boolean counts. Gson would read the string "true" as true and 1 as false,
     * so a typo could silently flip a lock trigger; anything else is reported and read as absent.
     */
    public static Boolean readStrictBoolean(JsonObject obj, String key, String where, List<String> problems) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return null;
        JsonElement v = obj.get(key);
        if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean()) return v.getAsBoolean();
        problems.add(where + " has \"" + key + "\": " + v + ", which is not true or false. Treated as not set.");
        return null;
    }

    private static TriggerCondition deserializeTrigger(JsonElement el, JsonDeserializationContext ctx,
                                                       List<String> problems) {
        if (!el.isJsonObject()) return null;
        JsonObject obj = el.getAsJsonObject();
        if (!obj.has("type") || obj.get("type").isJsonNull()) return null;
        String type = obj.get("type").getAsString();

        Class<? extends TriggerCondition> conditionClass = TriggerTypes.classFor(type);
        if (conditionClass == null) {
            // Not ours and not any loaded addon's. Keeping the object verbatim means the trigger
            // comes back when its mod does; the old code dropped it here, so editing a stage
            // without that mod installed destroyed it silently.
            return new UnknownTrigger(type, obj.deepCopy());
        }
        TriggerCondition parsed = ctx.deserialize(obj, conditionClass);
        boolean negate = Boolean.TRUE.equals(
                readStrictBoolean(obj, "negate", "The '" + type + "' trigger", problems));
        return negate && parsed != null ? new NegatedTrigger(parsed) : parsed;
    }

    public static JsonArray writeTriggers(List<TriggerCondition> triggers, JsonSerializationContext ctx) {
        JsonArray arr = new JsonArray();
        for (TriggerCondition t : triggers) {
            if (t instanceof UnknownTrigger unknown) {
                // Written back exactly as it was read, fields this build never understood included.
                // Also keeps a raw "negate" untouched.
                arr.add(unknown.raw().deepCopy());
                continue;
            }
            boolean negated = t instanceof NegatedTrigger;
            TriggerCondition inner = NegatedTrigger.unwrap(t);
            JsonObject json = ctx.serialize(inner).getAsJsonObject();
            // Ensure "type" is always present (records may not auto-include it).
            json.addProperty("type", inner.type());
            if (negated) json.addProperty("negate", true);
            arr.add(json);
        }
        return arr;
    }
}
