package net.bananemdnsa.historystages.api.team;

import net.minecraft.server.MinecraftServer;

import java.util.Set;
import java.util.UUID;

/**
 * Tells HistoryStages who plays together, so an individual stage flagged "share with team" can
 * reach the rest of a player's team.
 *
 * <p>Register one through {@link RegisterTeamProvidersEvent}. FTB Teams support ships built in;
 * another team mod plugs in the same way. With several providers the teammates are the union of
 * all their answers.
 *
 * <p>Return only teams a player chose to be in. Mods that give every player a team of their own
 * (FTB Teams does) must leave that solo team out, otherwise nobody is ever "alone" and nothing
 * changes — or worse, a server-wide team shares everything with everyone.
 */
@FunctionalInterface
public interface TeamProvider {

    /**
     * The other members of {@code player}'s team, online or not, without {@code player} itself.
     * Empty when the player is in no shared team. Called on the server thread.
     */
    Set<UUID> teammates(MinecraftServer server, UUID player);
}
