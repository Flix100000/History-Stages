package net.bananemdnsa.historystages.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link ServerPlayer} for a test to check things against.
 *
 * <p><strong>Not</strong> {@code GameTestHelper.makeMockServerPlayerInLevel()}, and the reason is
 * worth keeping: that helper runs the full login path — {@code CommonListenerCookie.createInitial}
 * and the {@code PlayerLoggedIn} event — and the dev runtime has FTB Quests, FTB Teams and Jade on
 * it, because this mod compiles against them. FTB Quests answers a login by sending the player a
 * packet, the fake player has no connection, and the test dies with
 * {@code Payload ftbquests:sync_quests_message may not be sent to the client}.
 *
 * <p>That failure says nothing about HistoryStages. The constructor below builds the player
 * directly and fires no events, which is also the more honest test: what is under examination is
 * the dependency checker, not the login flow.
 */
final class GameTestPlayers {

    private GameTestPlayers() {}

    /** A fresh player in the test's own level. Empty inventory, no XP, no stats. */
    static ServerPlayer create(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "gametest");
        return new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
    }

    /**
     * The same player with a connection that swallows everything sent to it.
     *
     * <p>For code under test that talks back — an actionbar line, a sync packet. Without it the
     * first send dies on the missing connection, after the part the test asks about.
     */
    static ServerPlayer createConnected(GameTestHelper helper) {
        ServerPlayer player = create(helper);
        new SilentListener(player);
        return player;
    }

    /** Gives a player built elsewhere (a test's own subclass) a connection that records what it is sent. */
    static void connectCapturing(ServerPlayer player, List<Packet<?>> sink) {
        new SilentListener(player, sink);
    }

    /**
     * Overridden on the listener, not only the connection: NeoForge checks a mod payload against
     * the channels the client negotiated before it ever reaches the connection, and a player
     * built without a login negotiated none.
     */
    private static final class SilentListener extends ServerGamePacketListenerImpl {
        private final @Nullable List<Packet<?>> sink;

        SilentListener(ServerPlayer player) {
            this(player, null);
        }

        SilentListener(ServerPlayer player, @Nullable List<Packet<?>> sink) {
            super(player.getServer(), recordingConnection(sink), player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
            this.sink = sink;
        }

        /** Hands vanilla packets to the sink as they would go on the wire. */
        private static Connection recordingConnection(@Nullable List<Packet<?>> sink) {
            if (sink == null) return new Connection(PacketFlow.SERVERBOUND);
            return new Connection(PacketFlow.SERVERBOUND) {
                @Override
                public void send(Packet<?> packet, @Nullable PacketSendListener listener, boolean flush) {
                    sink.add(packet);
                }
            };
        }

        @Override
        public void send(Packet<?> packet) {
            send(packet, null);
        }

        /**
         * A capturing player sends vanilla packets down the real path into the recording
         * connection. Mod payloads are recorded here: NeoForge refuses them for a player that
         * negotiated no channels.
         */
        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
            if (sink == null) return;
            if (packet instanceof ClientboundCustomPayloadPacket) {
                sink.add(packet);
            } else {
                super.send(packet, listener);
            }
        }
    }
}
