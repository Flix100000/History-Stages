package net.bananemdnsa.historystages.network.clientbound;

import net.bananemdnsa.historystages.client.editor.StageOverviewScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** How many unknown entries pruning all stages would remove, for the overview's confirmation. */
public record PruneUnknownPreviewPacket(int entries, int stages) {

    public static void encode(PruneUnknownPreviewPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entries);
        buf.writeVarInt(msg.stages);
    }

    public static PruneUnknownPreviewPacket decode(FriendlyByteBuf buf) {
        return new PruneUnknownPreviewPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(PruneUnknownPreviewPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Closed in the meantime: the answer has nothing left to ask.
            if (Minecraft.getInstance().screen instanceof StageOverviewScreen overview) {
                overview.onPrunePreview(msg.entries, msg.stages);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
