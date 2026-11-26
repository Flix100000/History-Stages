package net.bananemdnsa.historystages.network.serverbound;

import net.minecraft.server.permissions.Permissions;
import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.bananemdnsa.historystages.platform.IPayloadContext;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The editor's in-world slot pick: while it is on, clicks in a container select slots instead of
 * moving items, so the server must not act on them either.
 */
public record SlotPickModePacket(boolean active) implements CustomPacketPayload {

    private static final Set<UUID> PICKING = ConcurrentHashMap.newKeySet();

    public static final CustomPacketPayload.Type<SlotPickModePacket> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(HistoryStages.MOD_ID, "slot_pick_mode"));

    public static final StreamCodec<FriendlyByteBuf, SlotPickModePacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> buf.writeBoolean(msg.active), buf -> new SlotPickModePacket(buf.readBoolean()));

    public static void handle(SlotPickModePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            // Same bar as saving a stage: only editors may switch container clicks off.
            if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) return;
            if (msg.active) PICKING.add(player.getUUID()); else PICKING.remove(player.getUUID());
        });
    }

    public static boolean isPicking(UUID player) {
        return PICKING.contains(player);
    }

    public static void forget(UUID player) {
        PICKING.remove(player);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
