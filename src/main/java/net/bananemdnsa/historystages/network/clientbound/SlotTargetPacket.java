package net.bananemdnsa.historystages.network.clientbound;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.util.lock.SlotLockMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.bananemdnsa.historystages.platform.IPayloadContext;

/**
 * Tells the client which block the menu it just opened belongs to. The client's copy of a barrel
 * menu holds a plain container, so on its own it could only ever guess the menu type.
 */
public record SlotTargetPacket(int containerId, String target) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SlotTargetPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "slot_target"));

    public static final StreamCodec<FriendlyByteBuf, SlotTargetPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> { buf.writeVarInt(msg.containerId); buf.writeUtf(msg.target); },
                    buf -> new SlotTargetPacket(buf.readVarInt(), buf.readUtf())
            );

    public static void handle(SlotTargetPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var menu = ctx.player().containerMenu;
            if (menu != null && menu.containerId == msg.containerId) {
                ((SlotLockMenu) menu).historystages$setSlotTarget(msg.target);
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
