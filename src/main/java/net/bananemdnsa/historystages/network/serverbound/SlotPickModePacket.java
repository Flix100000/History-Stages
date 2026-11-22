package net.bananemdnsa.historystages.network.serverbound;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The editor's in-world slot pick: while it is on, clicks in a container select slots instead of
 * moving items, so the server must not act on them either.
 */
public record SlotPickModePacket(boolean active) {

    private static final Set<UUID> PICKING = ConcurrentHashMap.newKeySet();


    public static void encode(SlotPickModePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.active);
    }

    public static SlotPickModePacket decode(FriendlyByteBuf buf) {
        return new SlotPickModePacket(buf.readBoolean());
    }

    public static void handle(SlotPickModePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            // Same bar as saving a stage: only editors may switch container clicks off.
            if (!player.hasPermissions(2)) return;
            if (msg.active) PICKING.add(player.getUUID()); else PICKING.remove(player.getUUID());
        });
        ctx.get().setPacketHandled(true);
    }

    public static boolean isPicking(UUID player) {
        return PICKING.contains(player);
    }

    public static void forget(UUID player) {
        PICKING.remove(player);
    }
}
