package net.bananemdnsa.historystages.network.clientbound;

import net.bananemdnsa.historystages.client.cache.ClientZoneSelection;
import net.bananemdnsa.historystages.data.lock.ZoneSelection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * One player's zone selection, pushed to that player alone.
 *
 * <p>Never broadcast: where somebody is drawing a box is their business, and a locked zone's
 * geometry deliberately does not reach any client in this round.
 */
public record SyncZoneSelectionPacket(
        String dimension,
        boolean hasFirst, int firstX, int firstY, int firstZ,
        boolean hasSecond, int secondX, int secondY, int secondZ) {

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

    public static void encode(SyncZoneSelectionPacket msg, FriendlyByteBuf buf) {
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

    public static SyncZoneSelectionPacket decode(FriendlyByteBuf buf) {
        return new SyncZoneSelectionPacket(buf.readUtf(),
                buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt());
    }

    public static void handle(SyncZoneSelectionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientZoneSelection.update(msg.dimension,
                msg.hasFirst, msg.firstX, msg.firstY, msg.firstZ,
                msg.hasSecond, msg.secondX, msg.secondY, msg.secondZ));
        ctx.get().setPacketHandled(true);
    }
}
