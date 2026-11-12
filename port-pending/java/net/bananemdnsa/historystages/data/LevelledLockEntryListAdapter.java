package net.bananemdnsa.historystages.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import net.bananemdnsa.historystages.api.lock.LockActions;

/**
 * Reads and writes a stage's enchantment or effect list.
 *
 * <p>An entry with nothing but an id is written as a bare string, the same way items and fluids
 * are. Narrowed actions are stored as their complement, {@code unlock_actions}, taken against the
 * category's own vocabulary — which is why there is one subclass per category: against the wrong
 * list an entry would come back locking actions it never had.
 */
public abstract class LevelledLockEntryListAdapter extends TypeAdapter<List<LevelledLockEntry>> {

    private final List<String> vocabulary;
    private final List<String> stations;

    protected LevelledLockEntryListAdapter(List<String> vocabulary, List<String> stations) {
        this.vocabulary = vocabulary;
        this.stations = stations;
    }

    public static final class Enchantments extends LevelledLockEntryListAdapter {
        public Enchantments() {
            super(LockActions.ENCHANTMENT, List.of("enchanting_table", "anvil"));
        }
    }

    public static final class Effects extends LevelledLockEntryListAdapter {
        public Effects() {
            super(LockActions.EFFECT, List.of("brew"));
        }
    }

    @Override
    public void write(JsonWriter out, List<LevelledLockEntry> entries) throws IOException {
        if (entries == null) {
            out.nullValue();
            return;
        }
        out.beginArray();
        for (LevelledLockEntry entry : entries) {
            if (entry.getMinLevel() == null && entry.getLockActions() == null
                    && entry.getExcludedItemTypes() == null) {
                out.value(entry.getId());
                continue;
            }
            out.beginObject();
            out.name("id").value(entry.getId());
            if (entry.getMinLevel() != null) out.name("min_level").value(entry.getMinLevel());
            if (entry.getLockActions() != null) {
                out.name("unlock_actions").beginArray();
                for (String action : vocabulary) {
                    if (!entry.getLockActions().contains(action)) out.value(action);
                }
                out.endArray();
            }
            if (entry.getExcludedItemTypes() != null) {
                out.name("excluded_item_types").beginArray();
                for (String item : entry.getExcludedItemTypes()) out.value(item);
                out.endArray();
            }
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

            List<String> lockActions = null;
            if (obj.has("unlock_actions") && obj.get("unlock_actions").isJsonArray()) {
                List<String> unlocked = strings(obj, "unlock_actions");
                lockActions = new ArrayList<>();
                for (String action : vocabulary) {
                    if (!unlocked.contains(action)) lockActions.add(action);
                }
            } else if (obj.has("lock_items") && !obj.get("lock_items").getAsBoolean()) {
                // The first cut of this feature had a single switch instead of actions. It never
                // shipped, but stages made with it read as what the switch meant.
                lockActions = new ArrayList<>(stations);
            }

            List<String> excluded = obj.has("excluded_item_types") ? strings(obj, "excluded_item_types") : null;
            entries.add(new LevelledLockEntry(id, minLevel, lockActions, excluded));
        }
        in.endArray();
        return entries;
    }

    private static List<String> strings(JsonObject obj, String key) {
        List<String> values = new ArrayList<>();
        if (!obj.get(key).isJsonArray()) return values;
        for (JsonElement el : obj.getAsJsonArray(key)) values.add(el.getAsString());
        return values;
    }
}
