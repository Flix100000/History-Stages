package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.platform.IPayloadContext;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The client half of the packet registration.
 *
 * <p>It exists as its own class for one reason: ClientPlayNetworking is not on a dedicated
 * server, so a class that names it must never be loaded there. The codecs are registered on both
 * sides by {@link PacketHandler}; what is bound here is only who runs when a packet arrives.
 *
 * <p>It walks {@link PacketHandler#CLIENTBOUND} rather than listing the packets again, so a new
 * clientbound packet cannot be given a codec and then left without a receiver.
 */
@Environment(EnvType.CLIENT)
public final class ClientPacketHandler {

    private ClientPacketHandler() {}

    public static void register() {
        for (PacketHandler.Clientbound<?> packet : PacketHandler.CLIENTBOUND) {
            bind(packet);
        }
    }

    private static <T extends CustomPacketPayload> void bind(PacketHandler.Clientbound<T> packet) {
        ClientPlayNetworking.registerGlobalReceiver(packet.type(),
                (payload, context) -> packet.handler().accept(payload, IPayloadContext.of(context.player())));
    }

    /**
     * Sends a packet to the server. Only ever reached from the editor, the pedestal screen and the
     * client commands, all of which are client-only — which is why this sits here and not next to
     * the other send helpers.
     */
    public static void sendToServer(Object packet) {
        ClientPlayNetworking.send((CustomPacketPayload) packet);
    }
}
