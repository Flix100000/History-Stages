package net.bananemdnsa.historystages.api.team;

import net.bananemdnsa.historystages.data.team.TeamStageSharing;

import net.minecraft.server.level.ServerPlayer;

/**
 * What a {@link TeamProvider} reports back when its teams change.
 *
 * <p>Only joining matters: with {@code teamShareOnJoin} on, a new member is handed the shared
 * stages the team already has. Leaving takes nothing away, so there is nothing to report.
 */
public final class TeamChanges {

    private TeamChanges() {}

    /** Call on the server thread after {@code player} joined a team. Safe to call when the setting is off. */
    public static void joined(ServerPlayer player) {
        TeamStageSharing.onJoinedTeam(player);
    }
}
