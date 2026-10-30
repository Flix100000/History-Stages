package net.bananemdnsa.historystages.api.team;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.bananemdnsa.historystages.data.team.TeamProviders;
import net.bananemdnsa.historystages.data.team.TeamStageSharing;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side questions about teams, answered by whichever {@link TeamProvider} is active —
 * FTB Teams out of the box, or one an addon registered.
 */
public final class Teams {

    private Teams() {}

    /** This player's teammates, not counting the player. Empty without a team or a provider. */
    public static Set<UUID> teammates(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return Set.of();
        return teammates(player.getUUID(), server);
    }

    /** {@link #teammates(ServerPlayer)} by UUID, for a player who may be offline. */
    public static Set<UUID> teammates(UUID player, MinecraftServer server) {
        Set<UUID> mates = new HashSet<>(TeamProviders.teammates(server, player));
        mates.remove(player);
        return Set.copyOf(mates);
    }

    /** True if the two players are on the same team. */
    public static boolean areTeammates(UUID a, UUID b, MinecraftServer server) {
        return !a.equals(b) && teammates(a, server).contains(b);
    }

    /**
     * True if unlocking this individual stage passes it on to the player's team: the stage is set
     * to share and its mode allows it.
     */
    public static boolean isSharedWithTeam(String stageId) {
        return TeamStageSharing.isShared(stageId);
    }
}
