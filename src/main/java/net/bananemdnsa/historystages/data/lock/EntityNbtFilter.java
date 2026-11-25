package net.bananemdnsa.historystages.data.lock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Narrows an entity lock to entities whose saved data matches. Several alternatives may be given;
 * the lock bites when any one of them matches, which is how "oak or birch boats" is written.
 *
 * <p>In JSON the field is {@code nbt}, either one object or an array of objects. No field means
 * no filter: the lock applies to every entity of that type.
 *
 * <p>Kept free of Minecraft types so the data model stays testable. The actual comparison
 * against an entity is handed in as a predicate by whoever has the entity.
 */
public final class EntityNbtFilter {

    public static final EntityNbtFilter NONE = new EntityNbtFilter(List.of());

    private final List<JsonObject> alternatives;

    private EntityNbtFilter(List<JsonObject> alternatives) {
        this.alternatives = alternatives;
    }

    /** Copies the given objects; empty ones are dropped since they would match everything. */
    public static EntityNbtFilter of(@Nullable List<JsonObject> alternatives) {
        if (alternatives == null || alternatives.isEmpty()) return NONE;
        List<JsonObject> copy = new ArrayList<>(alternatives.size());
        for (JsonObject alt : alternatives) {
            if (alt != null && alt.size() > 0) copy.add(alt.deepCopy());
        }
        return copy.isEmpty() ? NONE : new EntityNbtFilter(Collections.unmodifiableList(copy));
    }

    /** Reads the {@code nbt} field: one object, an array of objects, or null for none. */
    public static EntityNbtFilter fromJson(@Nullable JsonElement json) {
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

    /**
     * True when the lock applies to this entity. Without a filter that is always the case. With
     * one, a caller that has no entity to look at (an addon asking by id alone) gets "no" — the
     * same answer an item NBT criterion gives on a stackless path.
     */
    public boolean matches(@Nullable Predicate<JsonObject> entityMatches) {
        if (alternatives.isEmpty()) return true;
        if (entityMatches == null) return false;
        for (JsonObject alt : alternatives) {
            if (entityMatches.test(alt)) return true;
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EntityNbtFilter other && alternatives.equals(other.alternatives);
    }

    @Override
    public int hashCode() {
        return alternatives.hashCode();
    }
}
