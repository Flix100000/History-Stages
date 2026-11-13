package net.bananemdnsa.historystages.data.relock;

import com.google.gson.*;
import net.bananemdnsa.historystages.data.auto.AutoTriggerCodec;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class LockTriggerAdapter implements JsonSerializer<LockTrigger>, JsonDeserializer<LockTrigger> {

    public static final Set<String> KNOWN_KEYS = Set.of("mode", "triggers", "re_unlockable");

    @Override
    public LockTrigger deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext ctx) {
        if (!json.isJsonObject()) {
            LockTrigger empty = new LockTrigger();
            empty.addReadProblem("lock_trigger is " + json + ", not an object. Ignored.");
            return empty;
        }
        JsonObject obj = json.getAsJsonObject();
        List<String> problems = new ArrayList<>();
        for (String key : obj.keySet()) {
            if (!KNOWN_KEYS.contains(key)) {
                problems.add("lock_trigger has unknown key '" + key + "'. Typo? Known keys: mode, triggers, re_unlockable.");
            }
        }
        Boolean reUnlockable = AutoTriggerCodec.readStrictBoolean(obj, "re_unlockable", "lock_trigger", problems);
        LockTrigger lt = new LockTrigger(AutoTriggerCodec.readMode(obj),
                AutoTriggerCodec.readTriggers(obj, ctx, problems), reUnlockable);
        problems.forEach(lt::addReadProblem);
        return lt;
    }

    @Override
    public JsonElement serialize(LockTrigger src, Type typeOfSrc, JsonSerializationContext ctx) {
        JsonObject out = new JsonObject();
        if (src.getRawMode() != null) out.addProperty("mode", src.getRawMode());
        out.add("triggers", AutoTriggerCodec.writeTriggers(src.getTriggers(), ctx));
        if (src.getRawReUnlockable() != null) out.addProperty("re_unlockable", src.getRawReUnlockable());
        return out;
    }
}
