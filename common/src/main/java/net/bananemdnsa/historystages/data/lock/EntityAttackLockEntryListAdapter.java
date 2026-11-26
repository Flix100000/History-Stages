package net.bananemdnsa.historystages.data.lock;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes the attack list. Entries without a filter stay plain strings, so a stage file
 * written before NBT filters existed looks exactly the same after a save.
 */
public class EntityAttackLockEntryListAdapter extends TypeAdapter<List<EntityAttackLockEntry>> {

    @Override
    public void write(JsonWriter out, List<EntityAttackLockEntry> entries) throws IOException {
        if (entries == null) {
            out.nullValue();
            return;
        }
        out.beginArray();
        for (EntityAttackLockEntry entry : entries) {
            JsonElement nbt = entry.getNbt().toJson();
            if (nbt == null) {
                out.value(entry.getId());
                continue;
            }
            out.beginObject();
            out.name("id").value(entry.getId());
            out.name("nbt");
            Streams.write(nbt, out);
            out.endObject();
        }
        out.endArray();
    }

    @Override
    public List<EntityAttackLockEntry> read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return new ArrayList<>();
        }
        List<EntityAttackLockEntry> entries = new ArrayList<>();
        in.beginArray();
        while (in.hasNext()) {
            if (in.peek() == JsonToken.STRING) {
                entries.addAll(LegacyBoatIds.expand(in.nextString(), EntityNbtFilter.NONE, EntityAttackLockEntry::new));
                continue;
            }
            JsonElement el = JsonParser.parseReader(in);
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            String id = obj.has("id") ? obj.get("id").getAsString() : "";
            entries.addAll(LegacyBoatIds.expand(id, EntityNbtFilter.fromJson(obj.get("nbt")), EntityAttackLockEntry::new));
        }
        in.endArray();
        return entries;
    }
}
