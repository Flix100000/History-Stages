package net.bananemdnsa.historystages.network.clientbound;

import net.bananemdnsa.historystages.client.cache.ClientLostStages;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/** Server to client: which stages are lost for good, globally and for this player. */
public record SyncLostStagesPacket(Set<String> global, Set<String> individual) {

    public static void encode(SyncLostStagesPacket msg, FriendlyByteBuf buf) {
        writeSet(buf, msg.global);
        writeSet(buf, msg.individual);
    }

    public static SyncLostStagesPacket decode(FriendlyByteBuf buf) {
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

    public static void handle(SyncLostStagesPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientLostStages.set(msg.global, msg.individual));
        ctx.get().setPacketHandled(true);
    }
}
