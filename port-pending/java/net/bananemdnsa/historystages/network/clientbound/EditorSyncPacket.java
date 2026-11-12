package net.bananemdnsa.historystages.network.clientbound;
import net.bananemdnsa.historystages.network.EditorDataCache;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.network.PacketJson;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * The stage set on its way to whoever opened the editor.
 *
 * <p>Gzipped for the same reason the login sync is: it carries the same map, so a pack big enough
 * to break one breaks the other. See {@link SyncStageDefinitionsPacket}.
 */
public record EditorSyncPacket(Map<String, StageEntry> stages) implements CustomPacketPayload {
    private static final Gson GSON = new Gson();
    private static final java.lang.reflect.Type MAP_TYPE = new TypeToken<Map<String, StageEntry>>() {}.getType();

    private static final int MAX_JSON_CHARS = 8 * 1024 * 1024;

    public static final CustomPacketPayload.Type<EditorSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "editor_sync"));

    public static final StreamCodec<FriendlyByteBuf, EditorSyncPacket> STREAM_CODEC =
            StreamCodec.of(EditorSyncPacket::encode, EditorSyncPacket::decode);

    private static void encode(FriendlyByteBuf buffer, EditorSyncPacket msg) {
        PacketJson.write(buffer, GSON.toJson(msg.stages), MAX_JSON_CHARS, "editor stages");
    }

    private static EditorSyncPacket decode(FriendlyByteBuf buffer) {
        String json = PacketJson.read(buffer, MAX_JSON_CHARS, "editor stages");
        Map<String, StageEntry> stages = GSON.fromJson(json, MAP_TYPE);
        if (stages == null) stages = new HashMap<>();
        return new EditorSyncPacket(stages);
    }

    public static void handle(EditorSyncPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            EditorDataCache.setStages(msg.stages);
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
