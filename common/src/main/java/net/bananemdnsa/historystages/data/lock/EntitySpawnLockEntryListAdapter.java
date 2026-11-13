package net.bananemdnsa.historystages.data.lock;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import net.bananemdnsa.historystages.data.lock.spawn.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Gson adapter for List&lt;EntitySpawnLockEntry&gt;.
 *
 * <p>An entry the old format can hold is written in the old format — a plain string, or an object
 * with {@code unlock_sources} / {@code unlock_dimensions} — so stage files that never use
 * SpawnControl stay byte-identical. Everything else goes under {@code phase}, {@code conditions}
 * and {@code extra_biomes}, and the dimension filter then lives in {@code conditions} only.
 *
 * <p>Read: also accepts the legacy {@code lock_sources} key. A value that can't be parsed is
 * dropped rather than failing the whole entry, and recorded on {@link EntitySpawnLockEntry#getReadProblems()}
 * for the validator (a later task) to surface to the user.
 */
public class EntitySpawnLockEntryListAdapter extends TypeAdapter<List<EntitySpawnLockEntry>> {

    @Override
    public void write(JsonWriter out, List<EntitySpawnLockEntry> entries) throws IOException {
        if (entries == null) {
            out.nullValue();
            return;
        }
        out.beginArray();
        for (EntitySpawnLockEntry entry : entries) {
            List<String> unlockedSources = new ArrayList<>();
            if (entry.hasLockSources()) {
                for (String src : EntitySpawnLockEntry.ALL_SOURCES) {
                    if (!entry.getLockSources().contains(src)) unlockedSources.add(src);
                }
            }
            boolean legacy = entry.isLegacyExpressible();

            if (legacy && unlockedSources.isEmpty() && !entry.hasUnlockDimensions()) {
                out.value(entry.getId());
                continue;
            }
            out.beginObject();
            out.name("id").value(entry.getId());
            if (entry.getPhase() != GenerationPhase.WHILE_LOCKED) {
                out.name("phase").value(entry.getPhase().serialize());
            }
            if (!unlockedSources.isEmpty()) writeStrings(out, "unlock_sources", unlockedSources);
            if (legacy) {
                if (entry.hasUnlockDimensions()) writeStrings(out, "unlock_dimensions", entry.getUnlockDimensions());
            } else {
                if (!entry.getConditions().isEmpty()) {
                    out.name("conditions");
                    writeConditions(out, entry.getConditions());
                }
                if (entry.getExtraBiomes() != null) {
                    out.name("extra_biomes");
                    writeExtra(out, entry.getExtraBiomes());
                }
            }
            out.endObject();
        }
        out.endArray();
    }

    private static void writeStrings(JsonWriter out, String name, List<String> values) throws IOException {
        out.name(name).beginArray();
        for (String v : values) out.value(v);
        out.endArray();
    }

    private static void writeConditions(JsonWriter out, SpawnConditions c) throws IOException {
        out.beginObject();
        if (c.dimensions() != null) writeFilter(out, "dimensions", c.dimensions());
        if (c.biomes() != null) writeFilter(out, "biomes", c.biomes());
        if (c.sky() != null) out.name("sky").value(c.sky().serialize());
        if (c.height() != null) writeRange(out, "y", c.height());
        if (c.time() != null) out.name("time").value(c.time().serialize());
        if (c.light() != null) writeRange(out, "light", c.light());
        if (c.weather() != null) out.name("weather").value(c.weather().serialize());
        if (!c.moonPhases().isEmpty()) {
            out.name("moon_phases").beginArray();
            for (int phase : new TreeSet<>(c.moonPhases())) out.value(phase);
            out.endArray();
        }
        out.endObject();
    }

    private static void writeFilter(JsonWriter out, String name, IdFilter filter) throws IOException {
        out.name(name).beginObject();
        out.name("mode").value(filter.mode().serialize());
        writeStrings(out, "ids", filter.ids());
        out.endObject();
    }

    private static void writeRange(JsonWriter out, String name, IntRange range) throws IOException {
        out.name(name).beginObject();
        out.name("min").value(range.min());
        out.name("max").value(range.max());
        out.endObject();
    }

    private static void writeExtra(JsonWriter out, ExtraBiomeSpawns extra) throws IOException {
        out.beginObject();
        writeStrings(out, "ids", extra.ids());
        if (extra.weight() != null) {
            out.name("weight").value(extra.weight().weight());
            out.name("min_group").value(extra.weight().minGroup());
            out.name("max_group").value(extra.weight().maxGroup());
        }
        if (extra.ignoreSpawnRules()) out.name("ignore_spawn_rules").value(true);
        out.endObject();
    }

    @Override
    public List<EntitySpawnLockEntry> read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return new ArrayList<>();
        }
        List<EntitySpawnLockEntry> entries = new ArrayList<>();
        in.beginArray();
        while (in.hasNext()) {
            if (in.peek() == JsonToken.STRING) {
                entries.add(new EntitySpawnLockEntry(in.nextString()));
                continue;
            }
            JsonObject obj = JsonParser.parseReader(in).getAsJsonObject();
            String id = obj.has("id") ? obj.get("id").getAsString() : "";

            List<String> lockSources = null;
            if (obj.has("unlock_sources") && obj.get("unlock_sources").isJsonArray()) {
                List<String> unlocked = strings(obj.get("unlock_sources"));
                lockSources = new ArrayList<>();
                for (String src : EntitySpawnLockEntry.ALL_SOURCES) {
                    if (!unlocked.contains(src)) lockSources.add(src);
                }
            } else if (obj.has("lock_sources") && obj.get("lock_sources").isJsonArray()) {
                lockSources = strings(obj.get("lock_sources"));
            }

            List<String> problems = new ArrayList<>();
            SpawnConditions conditions = obj.has("conditions") && obj.get("conditions").isJsonObject()
                    ? readConditions(obj.getAsJsonObject("conditions"), problems)
                    : SpawnConditions.EMPTY;
            if (conditions.dimensions() == null && obj.has("unlock_dimensions")) {
                List<String> dims = strings(obj.get("unlock_dimensions"));
                if (!dims.isEmpty()) conditions = conditions.withDimensions(IdFilter.only(dims));
            }

            GenerationPhase phase = GenerationPhase.parse(string(obj, "phase"));
            ExtraBiomeSpawns extra = obj.has("extra_biomes") && obj.get("extra_biomes").isJsonObject()
                    ? readExtra(obj.getAsJsonObject("extra_biomes"), problems)
                    : null;
            EntitySpawnLockEntry entry = new EntitySpawnLockEntry(id, lockSources, phase, conditions, extra);
            entries.add(problems.isEmpty() ? entry : entry.withReadProblems(problems));
        }
        in.endArray();
        return entries;
    }

    private static SpawnConditions readConditions(JsonObject obj, List<String> problems) {
        return new SpawnConditions(
                readFilter(obj, "dimensions", problems),
                readFilter(obj, "biomes", problems),
                readEnum(obj, "sky", SkyCondition::parse, problems),
                readRange(obj, "y", problems),
                readEnum(obj, "time", TimeOfDay::parse, problems),
                readRange(obj, "light", problems),
                readEnum(obj, "weather", WeatherCondition::parse, problems),
                readPhases(obj.get("moon_phases"), problems));
    }

    /** Common shape for the string-enum conditions; the raw value goes into the message either way. */
    private static <T> T readEnum(JsonObject obj, String key, java.util.function.Function<String, T> parser,
                                  List<String> problems) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) return null;
        String raw = el.isJsonPrimitive() ? el.getAsString() : el.toString();
        T value = parser.apply(raw);
        if (value == null) problems.add("condition '" + key + "' has unknown value \"" + raw + "\"");
        return value;
    }

    private static IdFilter readFilter(JsonObject parent, String key, List<String> problems) {
        JsonElement el = parent.get(key);
        if (el == null || !el.isJsonObject()) return null;
        JsonObject obj = el.getAsJsonObject();
        String rawMode = string(obj, "mode");
        FilterMode mode = FilterMode.parse(rawMode);
        if (mode == null) {
            problems.add("condition '" + key + "' has unknown mode \"" + rawMode + "\"");
            return null;
        }
        List<String> ids = strings(obj.get("ids"));
        return ids.isEmpty() ? null : new IdFilter(mode, ids);
    }

    private static IntRange readRange(JsonObject parent, String key, List<String> problems) {
        JsonElement el = parent.get(key);
        if (el == null) return null;
        Integer min = el.isJsonObject() ? integer(el.getAsJsonObject().get("min")) : null;
        Integer max = el.isJsonObject() ? integer(el.getAsJsonObject().get("max")) : null;
        if (min == null || max == null) {
            problems.add("condition '" + key + "' needs integer min and max");
            return null;
        }
        return new IntRange(min, max);
    }

    private static Set<Integer> readPhases(JsonElement el, List<String> problems) {
        Set<Integer> out = new HashSet<>();
        if (el != null && el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray()) {
                Integer phase = integer(item);
                if (phase != null) {
                    out.add(phase);
                } else {
                    String raw = item.isJsonPrimitive() ? item.getAsString() : item.toString();
                    problems.add("moon phase \"" + raw + "\" is not a number");
                }
            }
        }
        return out;
    }

    private static ExtraBiomeSpawns readExtra(JsonObject obj, List<String> problems) {
        Integer weight = integer(obj.get("weight"));
        Integer min = integer(obj.get("min_group"));
        Integer max = integer(obj.get("max_group"));
        boolean anyGiven = obj.has("weight") || obj.has("min_group") || obj.has("max_group");
        boolean allGiven = weight != null && min != null && max != null;
        if (anyGiven && !allGiven) problems.add("extra_biomes weight/min_group/max_group must be given together");
        SpawnWeight spawnWeight = allGiven ? new SpawnWeight(weight, min, max) : null;
        boolean ignore = obj.has("ignore_spawn_rules") && obj.get("ignore_spawn_rules").isJsonPrimitive()
                && obj.get("ignore_spawn_rules").getAsJsonPrimitive().isBoolean()
                && obj.get("ignore_spawn_rules").getAsBoolean();
        return new ExtraBiomeSpawns(strings(obj.get("ids")), spawnWeight, ignore);
    }

    private static List<String> strings(JsonElement el) {
        List<String> out = new ArrayList<>();
        if (el != null && el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray()) {
                if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) out.add(item.getAsString());
            }
        }
        return out;
    }

    private static String string(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString() ? el.getAsString() : null;
    }

    private static Integer integer(JsonElement el) {
        if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) return null;
        double d = el.getAsDouble();
        return d == Math.rint(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE ? (int) d : null;
    }
}
