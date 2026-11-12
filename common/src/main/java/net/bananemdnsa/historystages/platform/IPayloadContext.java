package net.bananemdnsa.historystages.platform;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * What a packet handler is given alongside its payload.
 *
 * <p>NeoForge hands over a context with a wide surface; the handlers in this mod use only a few
 * things from it, so that is what this carries. Keeping the parameter type and the method names
 * means the handler bodies carry over from the NeoForge main branch unchanged.
 */
public interface IPayloadContext {

    /**
     * The player this packet belongs to: the receiving player on the client, the sender on the
     * server. Handlers pattern-match it against ServerPlayer to tell the two apart, which is why
     * this is the common supertype rather than either specific one.
     */
    Player player();

    /** Runs work that has to be on the game thread; packets arrive on the network thread. */
    void enqueueWork(Runnable work);

    /** Sends a payload back the way this one came: to the sender on the server, to the server on the client. */
    void reply(CustomPacketPayload payload);

    /**
     * A context for code that is already on the game thread, such as a GameTest handing a payload
     * straight to its handler. Work runs at once.
     */
    static IPayloadContext of(Player player) {
        return new IPayloadContext() {
            @Override
            public Player player() {
                return player;
            }

            @Override
            public void enqueueWork(Runnable work) {
                work.run();
            }

            @Override
            public void reply(CustomPacketPayload payload) {
                if (player instanceof ServerPlayer serverPlayer) {
                    NetworkManager.sendToPlayer(serverPlayer, payload);
                } else {
                    NetworkManager.sendToServer(payload);
                }
            }
        };
    }

    /** Wraps the context Architectury hands to a receiver. */
    static IPayloadContext of(NetworkManager.PacketContext context) {
        return new IPayloadContext() {
            @Override
            public Player player() {
                return context.getPlayer();
            }

            @Override
            public void enqueueWork(Runnable work) {
                context.queue(work);
            }

            @Override
            public void reply(CustomPacketPayload payload) {
                if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
                    NetworkManager.sendToPlayer(serverPlayer, payload);
                } else {
                    NetworkManager.sendToServer(payload);
                }
            }
        };
    }
}
