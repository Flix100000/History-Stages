package net.bananemdnsa.historystages.network.clientbound;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.client.cache.ClientZoneShapes;
import net.bananemdnsa.historystages.data.lock.ZoneShape;
import net.bananemdnsa.historystages.data.lock.ZoneShapeType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * The locked zones near one player that the client has to know about.
 *
 * <p>Only what a zone allows: a zone that neither asks to be drawn nor puts up a barrier is never
 * sent, so an area meant as a surprise stays one even against somebody reading their own packets.
 * Only what is near, and only to a player the zone is actually locked for.
 */
public record SyncZoneShapesPacket(List<ClientZoneShapes.Visible> zones) {

    public static void encode(SyncZoneShapesPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.zones.size());
        for (ClientZoneShapes.Visible zone : msg.zones) {
            buf.writeBoolean(zone.border());
            buf.writeBoolean(zone.overlay());
            buf.writeBoolean(zone.barrier());
            buf.writeBoolean(zone.inverted());
            buf.writeVarInt(zone.shapes().size());
            for (ZoneShape shape : zone.shapes()) {
                buf.writeVarInt(shape.type().ordinal());
                buf.writeInt(shape.fromX());
                buf.writeInt(shape.fromY());
                buf.writeInt(shape.fromZ());
                buf.writeInt(shape.toX());
                buf.writeInt(shape.toY());
                buf.writeInt(shape.toZ());
                buf.writeInt(shape.radius());
                buf.writeInt(shape.height());
                buf.writeBoolean(shape.fullHeight());
            }
        }
    }

    public static SyncZoneShapesPacket decode(FriendlyByteBuf buf) {
        int zoneCount = buf.readVarInt();
        List<ClientZoneShapes.Visible> zones = new ArrayList<>(zoneCount);
        for (int i = 0; i < zoneCount; i++) {
            boolean border = buf.readBoolean();
            boolean overlay = buf.readBoolean();
            boolean barrier = buf.readBoolean();
            boolean inverted = buf.readBoolean();
            int shapeCount = buf.readVarInt();
            List<ZoneShape> shapes = new ArrayList<>(shapeCount);
            for (int s = 0; s < shapeCount; s++) {
                ZoneShapeType[] types = ZoneShapeType.values();
                int ordinal = buf.readVarInt();
                shapes.add(new ZoneShape(
                        types[Math.floorMod(ordinal, types.length)],
                        buf.readInt(), buf.readInt(), buf.readInt(),
                        buf.readInt(), buf.readInt(), buf.readInt(),
                        buf.readInt(), buf.readInt(), buf.readBoolean()));
            }
            zones.add(new ClientZoneShapes.Visible(border, overlay, barrier, inverted, shapes));
        }
        return new SyncZoneShapesPacket(zones);
    }

    public static void handle(SyncZoneShapesPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientZoneShapes.update(msg.zones));
        ctx.get().setPacketHandled(true);
    }
}
