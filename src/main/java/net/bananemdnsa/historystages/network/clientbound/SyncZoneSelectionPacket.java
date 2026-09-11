package net.bananemdnsa.historystages.network.clientbound;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.cache.ClientZoneSelection;
import net.bananemdnsa.historystages.data.lock.ZoneSelection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * One player's zone selection, pushed to that player alone.
 *
 * <p>Never broadcast: where somebody is drawing a box is their business, and a locked zone's
 * geometry deliberately does not reach any client in this round.
 */
public record SyncZoneSelectionPacket(
        String dimension,
        boolean hasFirst, int firstX, int firstY, int firstZ,
        boolean hasSecond, int secondX, int secondY, int secondZ) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncZoneSelectionPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "sync_zone_selection"));

    public static final StreamCodec<FriendlyByteBuf, SyncZoneSelectionPacket> STREAM_CODEC =
            StreamCodec.of(SyncZoneSelectionPacket::write, SyncZoneSelectionPacket::read);

    /** The empty selection, which is also how "I just cleared it" is expressed. */
    public static SyncZoneSelectionPacket empty() {
        return new SyncZoneSelectionPacket("", false, 0, 0, 0, false, 0, 0, 0);
    }

    public static SyncZoneSelectionPacket of(ZoneSelection.Selection selection) {
        if (selection == null) return empty();
        return new SyncZoneSelectionPacket(selection.dimension(),
                selection.hasFirst(), selection.firstX(), selection.firstY(), selection.firstZ(),
                selection.hasSecond(), selection.secondX(), selection.secondY(), selection.secondZ());
    }

    private static void write(FriendlyByteBuf buf, SyncZoneSelectionPacket msg) {
        buf.writeUtf(msg.dimension);
        buf.writeBoolean(msg.hasFirst);
        buf.writeInt(msg.firstX);
        buf.writeInt(msg.firstY);
        buf.writeInt(msg.firstZ);
        buf.writeBoolean(msg.hasSecond);
        buf.writeInt(msg.secondX);
        buf.writeInt(msg.secondY);
        buf.writeInt(msg.secondZ);
    }

    private static SyncZoneSelectionPacket read(FriendlyByteBuf buf) {
        return new SyncZoneSelectionPacket(buf.readUtf(),
                buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt());
    }

    public static void handle(SyncZoneSelectionPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientZoneSelection.update(msg.dimension,
                msg.hasFirst, msg.firstX, msg.firstY, msg.firstZ,
                msg.hasSecond, msg.secondX, msg.secondY, msg.secondZ));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
