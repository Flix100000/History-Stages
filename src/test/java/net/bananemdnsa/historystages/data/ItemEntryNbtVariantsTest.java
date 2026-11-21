package net.bananemdnsa.historystages.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.bananemdnsa.historystages.data.lock.EntityInteractionLockEntry;
import net.bananemdnsa.historystages.data.lock.EntityInteractionLockEntryListAdapter;
import net.bananemdnsa.historystages.data.lock.NamedLockEntry;
import net.bananemdnsa.historystages.data.lock.NamedLockEntryListAdapter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Item and tag lock entries with several NBT variants: storage and copies. */
class ItemEntryNbtVariantsTest {

    private static final Type ITEM_LIST = new TypeToken<List<ItemEntry>>() {}.getType();
    private static final Type NAMED_LIST = new TypeToken<List<NamedLockEntry>>() {}.getType();
    private static final Type INTERACT = new TypeToken<List<EntityInteractionLockEntry>>() {}.getType();
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(INTERACT, new EntityInteractionLockEntryListAdapter())
            .registerTypeAdapter(ITEM_LIST, new ItemEntryListAdapter())
            .registerTypeAdapter(NAMED_LIST, new NamedLockEntryListAdapter())
            .create();

    private static void assertRoundTrip(String json, Type type) {
        Object read = GSON.fromJson(json, type);
        assertEquals(JsonParser.parseString(json), JsonParser.parseString(GSON.toJson(read, type)));
    }

    @Test
    void anItemEntryWithTwoVariantsRoundTripsAsArray() {
        String json = "[{\"id\":\"tconstruct:pick_head\",\"nbt\":[{\"Material\":\"cobalt\"},{\"Material\":\"manyullyn\"}]}]";
        assertRoundTrip(json, ITEM_LIST);
        List<ItemEntry> read = GSON.fromJson(json, ITEM_LIST);
        assertEquals(2, read.get(0).getNbtFilter().alternatives().size());
    }

    @Test
    void singleObjectAndPlainItemEntriesStayAsTheyWere() {
        assertRoundTrip("[{\"id\":\"minecraft:potion\",\"nbt\":{\"Potion\":\"minecraft:swiftness\"}}]", ITEM_LIST);
        assertRoundTrip("[\"minecraft:stone\"]", ITEM_LIST);
    }

    @Test
    void aTagEntryWithTwoVariantsRoundTripsAsArray() {
        String json = "[{\"id\":\"#tconstruct:parts\",\"nbt\":[{\"Material\":\"cobalt\"},{\"Material\":\"manyullyn\"}]}]";
        assertRoundTrip(json, NAMED_LIST);
        List<NamedLockEntry> read = GSON.fromJson(json, NAMED_LIST);
        assertEquals(2, read.get(0).getNbtFilter().alternatives().size());
    }

    @Test
    void anArrayOfEmptyVariantsReadsAsNoFilter() {
        List<NamedLockEntry> tags = GSON.fromJson("[{\"id\":\"#c:ingots\",\"nbt\":[{}]}]", NAMED_LIST);
        assertFalse(tags.get(0).hasNbt());
        List<ItemEntry> items = GSON.fromJson("[{\"id\":\"minecraft:paper\",\"nbt\":[{}]}]", ITEM_LIST);
        assertFalse(items.get(0).hasNbt());
    }

    @Test
    void copiesKeepEveryVariant() {
        NbtFilter two = NbtFilter.fromJson(JsonParser.parseString("[{\"m\":\"a\"},{\"m\":\"b\"}]"));
        assertEquals(two, ItemEntry.withFilter("minecraft:paper", two, null, null, null).copy().getNbtFilter());
        assertEquals(two, NamedLockEntry.withFilter("#c:ingots", null, null, null, two).copy().getNbtFilter());
    }

    /** Interaction items get no variant button, but hand-written variants must survive a save. */
    @Test
    void handWrittenVariantsOnInteractionItemsSurvive() {
        assertRoundTrip("[{\"id\":\"minecraft:villager\",\"lock_items\":[{\"id\":\"minecraft:paper\","
                + "\"nbt\":[{\"Material\":\"cobalt\"},{\"Material\":\"manyullyn\"}]}]}]", INTERACT);
    }

    /** A string nbt (SNBT written by habit) used to fail loading; it must not quietly lock every stack. */
    @Test
    void aStringNbtOnAnItemEntryStillFailsLoudly() {
        assertThrows(com.google.gson.JsonParseException.class, () -> GSON.fromJson(
                "[{\"id\":\"minecraft:paper\",\"nbt\":\"{Material:cobalt}\"}]", ITEM_LIST));
    }
}
