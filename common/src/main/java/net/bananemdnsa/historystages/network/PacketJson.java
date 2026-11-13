package net.bananemdnsa.historystages.network;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.FriendlyByteBuf;

import java.io.IOException;

/**
 * Buffer glue for the gzipped JSON fields, shared by the two packets that carry a whole stage set.
 *
 * <p>Both used to write the map as a plain string, and both threw mid-encode once a pack's stages
 * outgrew the fixed character cap: the login sync dropped the player, and the editor sync dropped
 * whoever opened the editor. One shape for both, so the next field added to either does not have
 * to rediscover that.
 */
public final class PacketJson {

    /**
     * Ceiling on what actually travels, per field.
     *
     * <p>The real limit is Minecraft's, and it sits on the whole frame: with
     * {@code network-compression-threshold=-1} nothing else squeezes the packet, and the length
     * prefix tops out just under 2 MiB. A packet with eight of these fields still fits.
     */
    public static final int MAX_COMPRESSED_BYTES = 1024 * 1024;

    private PacketJson() {}

    /**
     * The failure this replaces was a bare "String too big" mid-encode, which drops the
     * connection with nothing in the log pointing at a stage file. Naming the field and both
     * numbers is the whole improvement — the throw still kills the packet either way.
     */
    public static void write(FriendlyByteBuf buffer, String json, int maxChars, String field) {
        if (json.length() > maxChars) {
            throw new EncoderException("History Stages: " + field + " is " + json.length()
                    + " characters, over the " + maxChars + " limit - split the stage set up");
        }
        try {
            byte[] payload = GzipJson.compress(json);
            if (payload.length > MAX_COMPRESSED_BYTES) {
                throw new EncoderException("History Stages: " + field + " is still "
                        + payload.length + " bytes compressed, over the " + MAX_COMPRESSED_BYTES
                        + " limit - split the stage set up");
            }
            buffer.writeByteArray(payload);
        } catch (IOException e) {
            throw new EncoderException("History Stages: failed to compress " + field, e);
        }
    }

    public static String read(FriendlyByteBuf buffer, int maxChars, String field) {
        try {
            return GzipJson.decompress(buffer.readByteArray(MAX_COMPRESSED_BYTES), maxChars);
        } catch (IOException e) {
            throw new DecoderException("History Stages: failed to read " + field, e);
        }
    }
}
