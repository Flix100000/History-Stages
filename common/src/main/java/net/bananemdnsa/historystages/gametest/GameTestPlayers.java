package net.bananemdnsa.historystages.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
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
public final class GameTestPlayers {

    private GameTestPlayers() {}

    /** A fresh player in the test's own level. Empty inventory, no XP, no stats. */
    public static ServerPlayer create(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "gametest");
        ServerPlayer player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        survival(player);
        return player;
    }

    /**
     * A new player gets the server's default mode, and the GameTest server's is creative. A
     * creative player keeps what it places and skips most locks, so every test here would ask the
     * wrong player.
     *
     * <p>Not {@code changeGameModeForPlayer}: it broadcasts a player-info packet built from the
     * player's connection, which {@link #create} players do not have.
     */
    static void survival(ServerPlayer player) {
        try {
            java.lang.reflect.Method set = net.minecraft.server.level.ServerPlayerGameMode.class.getDeclaredMethod(
                    "setGameModeForPlayer", net.minecraft.world.level.GameType.class, net.minecraft.world.level.GameType.class);
            set.setAccessible(true);
            set.invoke(player.gameMode, net.minecraft.world.level.GameType.SURVIVAL, null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not put the test player in survival", e);
        }
    }

    /**
     * The same player with a connection that swallows everything sent to it.
     *
     * <p>For code under test that talks back — an actionbar line, a sync packet. Without it the
     * first send dies on the missing connection, after the part the test asks about.
     */
    public static ServerPlayer createConnected(GameTestHelper helper) {
        ServerPlayer player = create(helper);
        new SilentListener(player);
        return player;
    }

    /** Gives a player built elsewhere (a test's own subclass) a connection that records what it is sent. */
    public static void connectCapturing(ServerPlayer player, java.util.List<Packet<?>> sink) {
        new SilentListener(player, sink);
    }

    /** A connected player whose vanilla packets land in {@code sink}, for tests about what is sent. */
    public static ServerPlayer createCapturing(GameTestHelper helper, java.util.List<Packet<?>> sink) {
        ServerPlayer player = create(helper);
        new SilentListener(player, sink);
        return player;
    }

    /**
     * Overridden on the listener, not only the connection: NeoForge checks a mod payload against
     * the channels the client negotiated before it ever reaches the connection, and a player
     * built without a login negotiated none.
     */
    private static final class SilentListener extends ServerGamePacketListenerImpl {
        private final @Nullable java.util.List<Packet<?>> sink;

        SilentListener(ServerPlayer player) {
            this(player, null);
        }

        SilentListener(ServerPlayer player, @Nullable java.util.List<Packet<?>> sink) {
            super(player.level().getServer(), recordingConnection(sink), player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
            this.sink = sink;
        }

        /** Hands vanilla packets to the sink as they would go on the wire. */
        private static Connection recordingConnection(@Nullable java.util.List<Packet<?>> sink) {
            if (sink == null) return new Connection(PacketFlow.SERVERBOUND);
            return new Connection(PacketFlow.SERVERBOUND) {
                @Override
                public void send(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush) {
                    sink.add(packet);
                }
            };
        }

        @Override
        public void send(Packet<?> packet) {
            send(packet, null);
        }

        // Interaction packets are dropped until the client reports its world as loaded.
        @Override
        public boolean hasClientLoaded() {
            return true;
        }

        // A pearl only teleports a thrower whose connection is live.
        @Override
        public boolean isAcceptingMessages() {
            return true;
        }

        /**
         * A capturing player sends vanilla packets down the real path (so anything hooked into it,
         * like the per-player recipe packet, applies) into the recording connection. Mod payloads
         * are recorded here: NeoForge refuses them for a player that negotiated no channels.
         */
        @Override
        public void send(Packet<?> packet, @Nullable ChannelFutureListener listener) {
            if (sink == null) return;
            if (packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket) {
                sink.add(packet);
            } else {
                super.send(packet, listener);
            }
        }
    }
}
