package net.bananemdnsa.historystages.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The shared NBT filter: object-or-array JSON and "any variant matches". */
class NbtFilterTest {

    private static JsonElement parse(String json) {
        return JsonParser.parseString(json);
    }

    @Test
    void singleVariantWritesPlainObject() {
        JsonElement single = parse("{\"m\":\"cobalt\"}");
        assertEquals(single, NbtFilter.fromJson(single).toJson());
    }

    @Test
    void severalVariantsWriteArray() {
        JsonElement several = parse("[{\"m\":\"cobalt\"},{\"m\":\"manyullyn\"}]");
        NbtFilter filter = NbtFilter.fromJson(several);
        assertEquals(2, filter.alternatives().size());
        assertEquals(several, filter.toJson());
    }

    @Test
    void emptyAndNonObjectAlternativesDropped() {
        NbtFilter mixed = NbtFilter.fromJson(parse("[{}, 3, {\"m\":\"cobalt\"}]"));
        assertEquals(1, mixed.alternatives().size());

        NbtFilter allEmpty = NbtFilter.fromJson(parse("[{}]"));
        assertTrue(allEmpty.isEmpty());
        assertNull(allEmpty.toJson());
    }

    @Test
    void matchesWhenAnyVariantMatches() {
        NbtFilter filter = NbtFilter.fromJson(parse("[{\"m\":\"cobalt\"},{\"m\":\"manyullyn\"}]"));
        assertTrue(filter.matches(c -> c.get("m").getAsString().equals("manyullyn")));
        assertFalse(filter.matches(c -> false));
        assertFalse(filter.matches(null));
        assertTrue(NbtFilter.NONE.matches(null));
    }

    @Test
    void singleWrapsOneObjectAndTreatsEmptyAsNone() {
        JsonObject one = parse("{\"m\":\"cobalt\"}").getAsJsonObject();
        assertEquals(1, NbtFilter.single(one).alternatives().size());
        assertSame(NbtFilter.NONE, NbtFilter.single(null));
        assertSame(NbtFilter.NONE, NbtFilter.single(new JsonObject()));
    }

    /** Stands in for a reflectively serialised holder such as a pedestal requirement. */
    private static final class Holder {
        NbtFilter nbt;
    }

    @Test
    void reflectiveGsonUsesAdapter() {
        Gson gson = new Gson();
        Holder two = new Holder();
        two.nbt = NbtFilter.fromJson(parse("[{\"m\":\"cobalt\"},{\"m\":\"manyullyn\"}]"));
        assertEquals(parse("{\"nbt\":[{\"m\":\"cobalt\"},{\"m\":\"manyullyn\"}]}"), parse(gson.toJson(two)));

        Holder none = new Holder();
        none.nbt = NbtFilter.NONE;
        assertEquals(parse("{}"), parse(gson.toJson(none)));

        assertEquals(1, gson.fromJson("{\"nbt\":{\"m\":\"cobalt\"}}", Holder.class).nbt.alternatives().size());
        assertEquals(2, gson.fromJson("{\"nbt\":[{\"m\":\"a\"},{\"m\":\"b\"}]}", Holder.class).nbt.alternatives().size());
    }
}
