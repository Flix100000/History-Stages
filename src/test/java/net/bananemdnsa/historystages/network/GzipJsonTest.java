package net.bananemdnsa.historystages.network;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GzipJsonTest {

    /** Roughly what a stage map looks like: the same handful of keys over and over. */
    private static String stageLikeJson(int entries) {
        StringBuilder sb = new StringBuilder("{\"stages\":[");
        for (int i = 0; i < entries; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"id\":\"minecraft:item_").append(i)
              .append("\",\"unlock_actions\":[\"use\",\"recipe\"]}");
        }
        return sb.append("]}").toString();
    }

    @Test
    void roundTripsPlainJson() throws IOException {
        String json = "{\"a\":1,\"b\":[\"x\",\"y\"]}";
        assertEquals(json, GzipJson.decompress(GzipJson.compress(json), 1024));
    }

    @Test
    void roundTripsNonAsciiText() throws IOException {
        String json = "{\"desc\":\"Erzverhüttung – Stufe 3 ⛏\",\"emoji\":\"🔥\"}";
        assertEquals(json, GzipJson.decompress(GzipJson.compress(json), 1024));
    }

    @Test
    void roundTripsEmptyObject() throws IOException {
        assertEquals("{}", GzipJson.decompress(GzipJson.compress("{}"), 1024));
    }

    /**
     * The reason this class exists: a stage set that blows the old 256K string cap has to fit on
     * the wire afterwards. 2000 entries is well past what the reporter's pack choked on.
     */
    @Test
    void shrinksAStageSetPastTheOldStringCap() throws IOException {
        String json = stageLikeJson(2000);
        assertTrue(json.length() > 65536, "test fixture is too small to prove anything");

        byte[] compressed = GzipJson.compress(json);

        assertTrue(compressed.length < json.length() / 8,
                "expected better than 8:1 on repetitive JSON, got "
                        + json.length() + " -> " + compressed.length);
        assertEquals(json, GzipJson.decompress(compressed, 8 * 1024 * 1024));
    }

    /**
     * Asserts the message from the in-loop guard, not just any failure. The finished string gets
     * length-checked too, so a test that only demanded "it throws" would stay green after the
     * streaming limit was deleted — which is the half that keeps the allocation bounded.
     */
    @Test
    void refusesAPayloadThatExpandsPastTheLimit() throws IOException {
        byte[] bomb = GzipJson.compress(stageLikeJson(5000));

        IOException thrown = assertThrows(IOException.class, () -> GzipJson.decompress(bomb, 1024));
        assertTrue(thrown.getMessage().contains("expands past the 1024"), thrown.getMessage());
    }

    /** The limit is a ceiling, not a budget — a payload that exactly fills it still decodes. */
    @Test
    void acceptsAPayloadExactlyAtTheLimit() throws IOException {
        String json = stageLikeJson(200);
        assertEquals(json, GzipJson.decompress(GzipJson.compress(json), json.length()));
    }
}
