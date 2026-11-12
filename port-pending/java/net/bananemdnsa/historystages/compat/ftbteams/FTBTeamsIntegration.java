package net.bananemdnsa.historystages.compat.ftbteams;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.api.team.RegisterTeamProvidersEvent;
import net.bananemdnsa.historystages.api.team.TeamChanges;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * FTB Teams as the built-in {@link net.bananemdnsa.historystages.api.team.TeamProvider}. Goes
 * through the public API only, the way an outside addon would.
 */
public final class FTBTeamsIntegration {

    private FTBTeamsIntegration() {}

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(RegisterTeamProvidersEvent.class, event ->
                event.register("ftbteams:party", FTBTeamsIntegration::teammates));
        TeamEvent.PLAYER_JOINED_PARTY.register(event -> TeamChanges.joined(event.getPlayer()));
    }

    private static Set<UUID> teammates(MinecraftServer server, UUID player) {
        if (!Config.GAMEPLAY.ftbTeamsEnabled.get() || !FTBTeamsAPI.api().isManagerLoaded()) return Set.of();
        // Every player has a solo "player team" in FTB Teams, and server teams are admin-made
        // groupings rather than a crew. Only a party is people who chose to play together.
        Team team = FTBTeamsAPI.api().getManager().getTeamForPlayerID(player)
                .filter(Team::isPartyTeam)
                .orElse(null);
        if (team == null) return Set.of();
        Set<UUID> mates = new HashSet<>(team.getMembers());
        mates.remove(player);
        return mates;
    }
}
