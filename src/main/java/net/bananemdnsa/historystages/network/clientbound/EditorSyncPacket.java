package net.bananemdnsa.historystages.network.clientbound;

import net.bananemdnsa.historystages.network.EditorDataCache;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.network.PacketJson;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The stage set on its way to whoever opened the editor.
 *
 * <p>Gzipped for the same reason the login sync is: it carries the same map, so a pack big enough
 * to break one breaks the other. See {@link SyncStageDefinitionsPacket}.
 */
public record EditorSyncPacket(Map<String, StageEntry> stages) {
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, StageEntry>>() {}.getType();

    private static final int MAX_JSON_CHARS = 8 * 1024 * 1024;

    public static void encode(EditorSyncPacket msg, FriendlyByteBuf buffer) {
        PacketJson.write(buffer, GSON.toJson(msg.stages), MAX_JSON_CHARS, "editor stages");
    }

    public static EditorSyncPacket decode(FriendlyByteBuf buffer) {
        String json = PacketJson.read(buffer, MAX_JSON_CHARS, "editor stages");
        Map<String, StageEntry> stages = GSON.fromJson(json, MAP_TYPE);
        if (stages == null) stages = new HashMap<>();
        return new EditorSyncPacket(stages);
    }

    public static void handle(EditorSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Update client-side stage data for the editor
            // We store this in a temporary holder that the editor screens can access
            EditorDataCache.setStages(msg.stages);
        });
        ctx.get().setPacketHandled(true);
    }
}
