package net.bananemdnsa.historystages.data.auto;

import com.google.gson.*;

import java.lang.reflect.Type;

public class AutoTriggerAdapter
        implements JsonSerializer<AutoTrigger>, JsonDeserializer<AutoTrigger> {

    @Override
    public AutoTrigger deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext ctx)
            throws JsonParseException {
        if (!json.isJsonObject()) return new AutoTrigger();
        JsonObject obj = json.getAsJsonObject();
        java.util.List<String> problems = new java.util.ArrayList<>();
        AutoTrigger at = new AutoTrigger(AutoTriggerCodec.readMode(obj), AutoTriggerCodec.readTriggers(obj, ctx, problems));
        problems.forEach(at::addReadProblem);
        return at;
    }

    @Override
    public JsonElement serialize(AutoTrigger src, Type typeOfSrc, JsonSerializationContext ctx) {
        JsonObject out = new JsonObject();
        if (src.getRawMode() != null) out.addProperty("mode", src.getRawMode());
        out.add("triggers", AutoTriggerCodec.writeTriggers(src.getTriggers(), ctx));
        return out;
    }
}
