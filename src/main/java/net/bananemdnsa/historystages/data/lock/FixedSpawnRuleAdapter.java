package net.bananemdnsa.historystages.data.lock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.google.gson.internal.Streams;
import net.bananemdnsa.historystages.data.lock.spawn.FixedSpawnRule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code {"rows": ["phase", "source.natural", "dimensions"], "rule": {...}}}. The rule is written
 * the way a spawnlock entry is, without its id, so the conditions read the same in both places.
 *
 * <p>The sources are the exception: the rule spells out {@code lock_sources} instead of the
 * entries' {@code unlock_sources}. An entry cannot lock no source at all, but a stage can fix every
 * source as allowed, and the complement form would read that back as "all locked".
 */
public class FixedSpawnRuleAdapter extends TypeAdapter<FixedSpawnRule> {

    private static final EntitySpawnLockEntryListAdapter ENTRIES = new EntitySpawnLockEntryListAdapter();

    @Override
    public void write(JsonWriter out, FixedSpawnRule rule) throws IOException {
        if (rule == null || rule.isEmpty()) {
            out.nullValue();
            return;
        }
        JsonObject obj = new JsonObject();
        JsonArray rows = new JsonArray();
        for (String row : rule.rows()) rows.add(row);
        obj.add("rows", rows);

        JsonElement written = ENTRIES.toJsonTree(List.of(rule.values())).getAsJsonArray().get(0);
        JsonObject values = written.isJsonObject() ? written.getAsJsonObject() : new JsonObject();
        values.remove("id");
        values.remove("unlock_sources");
        JsonArray locked = new JsonArray();
        for (String source : FixedSpawnRule.lockedSources(rule.values())) locked.add(source);
        values.add("lock_sources", locked);
        obj.add("rule", values);

        Streams.write(obj, out);
    }

    @Override
    public FixedSpawnRule read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        JsonElement parsed = JsonParser.parseReader(in);
        if (!parsed.isJsonObject()) return null;
        JsonObject obj = parsed.getAsJsonObject();

        List<String> rows = new ArrayList<>();
        if (obj.has("rows") && obj.get("rows").isJsonArray()) {
            for (JsonElement row : obj.getAsJsonArray("rows")) {
                if (row.isJsonPrimitive()) rows.add(row.getAsString());
            }
        }
        if (rows.isEmpty()) return null;

        JsonObject values = obj.has("rule") && obj.get("rule").isJsonObject()
                ? obj.getAsJsonObject("rule").deepCopy() : new JsonObject();
        List<String> locked = null;
        if (values.has("lock_sources") && values.get("lock_sources").isJsonArray()) {
            locked = new ArrayList<>();
            for (JsonElement source : values.getAsJsonArray("lock_sources")) {
                if (source.isJsonPrimitive()) locked.add(source.getAsString());
            }
            values.remove("lock_sources");
        }
        values.addProperty("id", "*");
        JsonArray wrapped = new JsonArray();
        wrapped.add(values);
        EntitySpawnLockEntry entry = ENTRIES.fromJsonTree(wrapped).get(0);
        if (locked != null) entry = entry.withLockedSources(locked);
        return new FixedSpawnRule(rows, entry);
    }
}
