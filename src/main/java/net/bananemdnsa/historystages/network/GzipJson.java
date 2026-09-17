package net.bananemdnsa.historystages.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Gzip for the JSON payloads that ride in packets.
 *
 * <p>Deliberately free of any Minecraft type: the buffer glue lives at the call site so this half
 * stays reachable from a plain unit test.
 */
public final class GzipJson {

    /**
     * Worst case UTF-8 bytes per Java char. Three, not four — a supplementary character costs
     * four bytes but occupies two chars, so the per-char ceiling stays at three.
     */
    private static final int MAX_BYTES_PER_CHAR = 3;

    private static final int CHUNK = 8192;

    private GzipJson() {}

    public static byte[] compress(String json) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, json.length() / 8));
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(json.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    /**
     * Inflates {@code payload} and refuses anything longer than {@code maxChars}.
     *
     * <p>The limit is enforced while inflating rather than on the finished string. A few hundred
     * bytes of gzip can expand to gigabytes, and a client decoding a packet from a server it does
     * not control must not be talked into allocating that.
     */
    public static String decompress(byte[] payload, int maxChars) throws IOException {
        long maxBytes = (long) maxChars * MAX_BYTES_PER_CHAR;
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, payload.length * 4));

        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(payload))) {
            byte[] chunk = new byte[CHUNK];
            int read;
            while ((read = gzip.read(chunk)) != -1) {
                if (out.size() + read > maxBytes) {
                    throw new IOException("Compressed JSON expands past the " + maxChars
                            + " character limit");
                }
                out.write(chunk, 0, read);
            }
        }

        String json = out.toString(StandardCharsets.UTF_8);
        if (json.length() > maxChars) {
            throw new IOException("Decompressed JSON is " + json.length()
                    + " characters, limit is " + maxChars);
        }
        return json;
    }
}
