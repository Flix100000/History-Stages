package net.bananemdnsa.historystages.network.clientbound;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.editor.StageOverviewScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.bananemdnsa.historystages.platform.IPayloadContext;

/** How many unknown entries pruning all stages would remove, for the overview's confirmation. */
public record PruneUnknownPreviewPacket(int entries, int stages) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PruneUnknownPreviewPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(HistoryStages.MOD_ID, "prune_unknown_preview"));

    public static final StreamCodec<FriendlyByteBuf, PruneUnknownPreviewPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> {
                        buf.writeVarInt(msg.entries);
                        buf.writeVarInt(msg.stages);
                    },
                    buf -> new PruneUnknownPreviewPacket(buf.readVarInt(), buf.readVarInt()));

    public static void handle(PruneUnknownPreviewPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            // Closed in the meantime: the answer has nothing left to ask.
            if (Minecraft.getInstance().gui.screen() instanceof StageOverviewScreen overview) {
                overview.onPrunePreview(msg.entries, msg.stages);
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
