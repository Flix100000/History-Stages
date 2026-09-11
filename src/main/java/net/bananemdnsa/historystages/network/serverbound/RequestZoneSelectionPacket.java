package net.bananemdnsa.historystages.network.serverbound;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.lock.ZoneSelection;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.SyncZoneSelectionPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The zone editor asking for the marking this player left in the world.
 *
 * <p>Sent when the editor opens rather than relying on the push alone: a corner marked before the
 * screen was ever opened has no push to ride on, and neither does one marked in a session the
 * client has since reconnected in.
 */
public record RequestZoneSelectionPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestZoneSelectionPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "request_zone_selection"));

    public static final StreamCodec<FriendlyByteBuf, RequestZoneSelectionPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> {}, buf -> new RequestZoneSelectionPacket());

    public static void handle(RequestZoneSelectionPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;
            PacketHandler.sendZoneSelection(
                    SyncZoneSelectionPacket.of(ZoneSelection.of(player.getUUID())), player);
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
