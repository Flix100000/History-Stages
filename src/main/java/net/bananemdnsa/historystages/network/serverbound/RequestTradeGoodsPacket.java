package net.bananemdnsa.historystages.network.serverbound;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.lock.TradeGoodsScanner;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.SyncTradeGoodsPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * The editor asking which items merchants here deal in.
 *
 * <p>Asked rather than pushed, because almost nobody opens the trades tab and rolling the answer
 * costs a moment. The first ask pays for it; every later one reads the server's cached list.
 *
 * <p>Operator-gated like the rest of the editor's traffic. Nothing here is secret, but a packet
 * that makes the server do work on request is one any client could send.
 */
public record RequestTradeGoodsPacket() {

    public static void encode(RequestTradeGoodsPacket msg, FriendlyByteBuf buf) { }

    public static RequestTradeGoodsPacket decode(FriendlyByteBuf buf) {
        return new RequestTradeGoodsPacket();
    }

    public static void handle(RequestTradeGoodsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!player.hasPermissions(2)) return;
            PacketHandler.sendTradeGoodsToPlayer(
                    new SyncTradeGoodsPacket(TradeGoodsScanner.cached(player.level())), player);
        });
        ctx.get().setPacketHandled(true);
    }
}
