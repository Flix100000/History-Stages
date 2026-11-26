package net.bananemdnsa.historystages.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * NBT criteria on a lock entry or requirement, as one or more alternatives. The entry applies when
 * any one of them matches, which is how "oak or birch boats" or "parts made of cobalt or
 * manyullyn" is written. Used by entity locks and by item, tag and pedestal item entries.
 *
 * <p>In JSON the field is {@code nbt}, either one object or an array of objects. No field means
 * no filter: the entry applies to everything with that id.
 *
 * <p>Kept free of Minecraft types so the data model stays testable. The actual comparison
 * against an entity or a stack is handed in as a predicate by whoever has it.
 */
@JsonAdapter(NbtFilter.GsonAdapter.class)
public final class NbtFilter {

    public static final NbtFilter NONE = new NbtFilter(List.of());

    private final List<JsonObject> alternatives;

    private NbtFilter(List<JsonObject> alternatives) {
        this.alternatives = alternatives;
    }

    /** Copies the given objects; empty ones are dropped since they would match everything. */
    public static NbtFilter of(@Nullable List<JsonObject> alternatives) {
        if (alternatives == null || alternatives.isEmpty()) return NONE;
        List<JsonObject> copy = new ArrayList<>(alternatives.size());
        for (JsonObject alt : alternatives) {
            if (alt != null && alt.size() > 0) copy.add(alt.deepCopy());
        }
        return copy.isEmpty() ? NONE : new NbtFilter(Collections.unmodifiableList(copy));
    }

    /** One criteria object as a filter; null or empty means none. */
    public static NbtFilter single(@Nullable JsonObject criteria) {
        if (criteria == null || criteria.size() == 0) return NONE;
        return of(List.of(criteria));
    }

    /** Reads the {@code nbt} field: one object, an array of objects, or null for none. */
    public static NbtFilter fromJson(@Nullable JsonElement json) {
        if (json == null || json.isJsonNull()) return NONE;
        List<JsonObject> alts = new ArrayList<>();
        if (json.isJsonObject()) {
            alts.add(json.getAsJsonObject());
        } else if (json.isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray()) {
                if (el.isJsonObject()) alts.add(el.getAsJsonObject());
            }
        }
        return of(alts);
    }

    /**
     * {@link #fromJson} for fields that always rejected anything but an object. A string here is
     * usually SNBT written by habit; reading it as "no filter" would widen the entry to every
     * stack without a word, so it fails loading the way it did before variants.
     */
    public static NbtFilter fromJsonStrict(@Nullable JsonElement json) {
        if (json != null && !json.isJsonNull() && !json.isJsonObject() && !json.isJsonArray()) {
            throw new JsonParseException("nbt must be an object or an array of objects, got: " + json);
        }
        return fromJson(json);
    }

    /** The {@code nbt} field as written: a single object stays an object. Null when empty. */
    @Nullable
    public JsonElement toJson() {
        if (alternatives.isEmpty()) return null;
        if (alternatives.size() == 1) return alternatives.get(0).deepCopy();
        JsonArray array = new JsonArray();
        for (JsonObject alt : alternatives) array.add(alt.deepCopy());
        return array;
    }

    public boolean isEmpty() {
        return alternatives.isEmpty();
    }

    public List<JsonObject> alternatives() {
        List<JsonObject> copy = new ArrayList<>(alternatives.size());
        for (JsonObject alt : alternatives) copy.add(alt.deepCopy());
        return copy;
    }

    /** The first alternative, or null — only for callers that predate variants. */
    @Nullable
    public JsonObject first() {
        return alternatives.isEmpty() ? null : alternatives.get(0).deepCopy();
    }

    /**
     * True when the entry applies to the thing being checked. Without a filter that is always the
     * case. With one, a caller that has nothing to look at (an addon asking by id alone) gets "no"
     * — the same answer an item NBT criterion gives on a stackless path.
     */
    public boolean matches(@Nullable Predicate<JsonObject> criteriaMatch) {
        if (alternatives.isEmpty()) return true;
        if (criteriaMatch == null) return false;
        for (JsonObject alt : alternatives) {
            if (criteriaMatch.test(alt)) return true;
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NbtFilter other && alternatives.equals(other.alternatives);
    }

    @Override
    public int hashCode() {
        return alternatives.hashCode();
    }

    /** Lets reflective Gson (pedestal requirements) write and read the object-or-array form. */
    public static final class GsonAdapter extends TypeAdapter<NbtFilter> {
        @Override
        public void write(JsonWriter out, NbtFilter filter) throws IOException {
            JsonElement json = filter == null ? null : filter.toJson();
            if (json == null) {
                out.nullValue();
            } else {
                Streams.write(json, out);
            }
        }

        @Override
        public NbtFilter read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return NONE;
            }
            return NbtFilter.fromJsonStrict(JsonParser.parseReader(in));
        }
    }
}
