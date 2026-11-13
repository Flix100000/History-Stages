package net.bananemdnsa.historystages.fabric.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jspecify.annotations.Nullable;

/**
 * A survival {@link ServerPlayer} with a connection that swallows every packet. Same as the
 * NeoForge side's helper; not a test class, so it must stay out of the {@code fabric-gametest}
 * entrypoint list (the test API instantiates every class listed there).
 */
final class TestPlayers {

    private TestPlayers() {}

    static ServerPlayer createConnected(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "gametest"), ClientInformation.createDefault());
        new SilentListener(player);
        return player;
    }

    private static final class SilentListener extends ServerGamePacketListenerImpl {
        SilentListener(ServerPlayer player) {
            super(player.level().getServer(), new Connection(PacketFlow.SERVERBOUND), player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        // Interaction packets are dropped until the client reports its world as loaded.
        @Override
        public boolean hasClientLoaded() {
            return true;
        }

        @Override
        public void send(Packet<?> packet) {}

        @Override
        public void send(Packet<?> packet, @Nullable ChannelFutureListener listener) {}
    }
}
