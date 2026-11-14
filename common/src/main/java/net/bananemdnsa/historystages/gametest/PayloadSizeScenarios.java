package net.bananemdnsa.historystages.gametest;

import dev.architectury.impl.NetworkAggregator;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.bananemdnsa.historystages.network.serverbound.SaveStagePacket;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * A stage near the editor's size limit still goes to the server in packets vanilla accepts.
 * Vanilla refuses a serverbound custom payload over 32 KiB and drops the connection.
 */
public final class PayloadSizeScenarios {

    private static final int SERVERBOUND_LIMIT = 32767;

    private PayloadSizeScenarios() {}

    public static void aBigStageSaveFitsTheServerboundLimit(GameTestHelper helper) {
        String json = "{\"items\":[" + "\"minecraft:stone\",".repeat(3000) + "\"minecraft:dirt\"]}";
        SaveStagePacket save = new SaveStagePacket("gametest:big", json, false, false, "");

        List<Packet<?>> packets = new ArrayList<>();
        NetworkManager.collectPackets(packets::add, NetworkManager.c2s(), save, helper.getLevel().registryAccess());

        for (Packet<?> packet : packets) {
            int size = size(packet);
            if (size > SERVERBOUND_LIMIT) {
                helper.fail(Component.literal("a " + json.length() + "-character stage save left as a "
                        + size + "-byte packet; the server refuses anything over " + SERVERBOUND_LIMIT));
                return;
            }
        }
        helper.succeed();
    }

    private static int size(Packet<?> packet) {
        CustomPacketPayload payload = packet instanceof ServerboundCustomPayloadPacket custom ? custom.payload() : null;
        if (payload instanceof NetworkAggregator.BufCustomPacketPayload buf) return buf.payload().length;
        if (payload instanceof SaveStagePacket save) {
            FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
            SaveStagePacket.STREAM_CODEC.encode(out, save);
            return out.readableBytes();
        }
        throw new IllegalStateException("unexpected packet " + packet);
    }
}
