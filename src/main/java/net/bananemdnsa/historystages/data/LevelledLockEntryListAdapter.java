package net.bananemdnsa.historystages.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * Reads and writes a stage's enchantment or effect list.
 *
 * <p>An entry with nothing but an id is written as a bare string, the same way items and fluids
 * are, so the common case stays readable in a hand-edited file. {@code lock_items} is only
 * written when it is off, because on is what a bare id means.
 */
public class LevelledLockEntryListAdapter extends TypeAdapter<List<LevelledLockEntry>> {

    @Override
    public void write(JsonWriter out, List<LevelledLockEntry> entries) throws IOException {
        if (entries == null) {
            out.nullValue();
            return;
        }
        out.beginArray();
        for (LevelledLockEntry entry : entries) {
            if (entry.getMinLevel() == null && entry.isLockItems()) {
                out.value(entry.getId());
                continue;
            }
            out.beginObject();
            out.name("id").value(entry.getId());
            if (entry.getMinLevel() != null) out.name("min_level").value(entry.getMinLevel());
            if (!entry.isLockItems()) out.name("lock_items").value(false);
            out.endObject();
        }
        out.endArray();
    }

    @Override
    public List<LevelledLockEntry> read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return new ArrayList<>();
        }
        List<LevelledLockEntry> entries = new ArrayList<>();
        in.beginArray();
        while (in.hasNext()) {
            if (in.peek() == JsonToken.STRING) {
                entries.add(new LevelledLockEntry(in.nextString()));
                continue;
            }
            JsonObject obj = JsonParser.parseReader(in).getAsJsonObject();
            String id = obj.has("id") ? obj.get("id").getAsString() : "";
            Integer minLevel = obj.has("min_level") ? obj.get("min_level").getAsInt() : null;
            boolean lockItems = !obj.has("lock_items") || obj.get("lock_items").getAsBoolean();
            entries.add(new LevelledLockEntry(id, minLevel, lockItems));
        }
        in.endArray();
        return entries;
    }
}
