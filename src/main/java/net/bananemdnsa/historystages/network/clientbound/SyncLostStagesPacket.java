package net.bananemdnsa.historystages.network.clientbound;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.cache.ClientLostStages;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.Set;

/** Server to client: which stages are lost for good, globally and for this player. */
public record SyncLostStagesPacket(Set<String> global, Set<String> individual) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncLostStagesPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "sync_lost_stages"));

    public static final StreamCodec<FriendlyByteBuf, SyncLostStagesPacket> STREAM_CODEC =
            StreamCodec.of(SyncLostStagesPacket::encode, SyncLostStagesPacket::decode);

    private static void encode(FriendlyByteBuf buf, SyncLostStagesPacket msg) {
        writeSet(buf, msg.global);
        writeSet(buf, msg.individual);
    }

    private static SyncLostStagesPacket decode(FriendlyByteBuf buf) {
        // Evaluation order matters: the two reads consume the buffer in sequence.
        Set<String> global = readSet(buf);
        return new SyncLostStagesPacket(global, readSet(buf));
    }

    private static void writeSet(FriendlyByteBuf buf, Set<String> set) {
        buf.writeVarInt(set.size());
        for (String s : set) buf.writeUtf(s);
    }

    private static Set<String> readSet(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        Set<String> out = new HashSet<>(n);
        for (int i = 0; i < n; i++) out.add(buf.readUtf());
        return out;
    }

    public static void handle(SyncLostStagesPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientLostStages.set(msg.global, msg.individual));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
