package net.bananemdnsa.historystages.network.serverbound;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.lock.ZoneSelection;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.SyncZoneSelectionPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * The zone editor asking for the marking this player left in the world.
 *
 * <p>Sent when the editor opens rather than relying on the push alone: a corner marked before the
 * screen was ever opened has no push to ride on, and neither does one marked in a session the
 * client has since reconnected in.
 */
public record RequestZoneSelectionPacket() {

    public static void encode(RequestZoneSelectionPacket msg, FriendlyByteBuf buf) {}

    public static RequestZoneSelectionPacket decode(FriendlyByteBuf buf) {
        return new RequestZoneSelectionPacket();
    }


    public static void handle(RequestZoneSelectionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!player.hasPermissions(2)) return;
            PacketHandler.sendZoneSelection(
                    SyncZoneSelectionPacket.of(ZoneSelection.of(player.getUUID())), player);
        });
        ctx.get().setPacketHandled(true);
    }
}
