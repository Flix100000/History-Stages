package net.bananemdnsa.historystages.network.serverbound;

import dev.architectury.networking.NetworkManager;
import net.minecraft.server.permissions.Permissions;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.network.clientbound.SyncIndividualStatesPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.bananemdnsa.historystages.platform.IPayloadContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Client → Server: asks for every online player's individual-stage set. Lightweight
 * (no payload); the server replies with {@link SyncIndividualStatesPacket}. Sent by
 * the stage overview editor on open and on its ~1s poll tick.
 */
public record RequestIndividualStatesPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestIndividualStatesPacket> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(HistoryStages.MOD_ID, "request_individual_states"));

    public static final StreamCodec<FriendlyByteBuf, RequestIndividualStatesPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> {}, buf -> new RequestIndividualStatesPacket());

    public static void handle(RequestIndividualStatesPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) return;

            IndividualStageData data = IndividualStageData.get(player.level());
            Map<UUID, Set<String>> states = new HashMap<>();
            for (ServerPlayer online : player.level().getServer().getPlayerList().getPlayers()) {
                states.put(online.getUUID(), new HashSet<>(data.getUnlockedStages(online.getUUID())));
            }
            NetworkManager.sendToPlayer(player, new SyncIndividualStatesPacket(states));
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
