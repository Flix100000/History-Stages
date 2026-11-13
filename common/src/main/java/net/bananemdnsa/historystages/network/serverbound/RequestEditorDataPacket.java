package net.bananemdnsa.historystages.network.serverbound;
import dev.architectury.networking.NetworkManager;
import net.minecraft.server.permissions.Permissions;
import net.bananemdnsa.historystages.network.clientbound.EditorSyncPacket;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.bananemdnsa.historystages.platform.IPayloadContext;

public record RequestEditorDataPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestEditorDataPacket> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(HistoryStages.MOD_ID, "request_editor_data"));

    public static final StreamCodec<FriendlyByteBuf, RequestEditorDataPacket> STREAM_CODEC =
            StreamCodec.of(RequestEditorDataPacket::encode, RequestEditorDataPacket::decode);

    private static void encode(FriendlyByteBuf buffer, RequestEditorDataPacket msg) {
        // No data needed
    }

    private static RequestEditorDataPacket decode(FriendlyByteBuf buffer) {
        return new RequestEditorDataPacket();
    }

    public static void handle(RequestEditorDataPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) return;
                NetworkManager.sendToPlayer(player, new EditorSyncPacket(StageManager.getStages()));
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
