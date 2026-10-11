package net.bananemdnsa.historystages.api.team;

import net.bananemdnsa.historystages.data.diagnostics.AddonRegistrations;
import net.bananemdnsa.historystages.data.diagnostics.ApiExtension;
import net.bananemdnsa.historystages.data.diagnostics.ApiOwners;
import net.bananemdnsa.historystages.data.team.TeamProviders;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/**
 * Fired once during common setup so other mods can tell HistoryStages about their teams.
 *
 * <p>Registration is legal only during dispatch; the registry freezes when it ends.
 *
 * <pre>{@code
 * modEventBus.addListener(RegisterTeamProvidersEvent.class, event ->
 *         event.register("mymod:parties", (server, player) -> MyParties.membersOf(player)));
 * }</pre>
 *
 * <p>If your mod also knows when a player joins a team, call {@link TeamChanges#joined} from
 * there, so the "new members catch up" setting works with your teams too.
 */
public class RegisterTeamProvidersEvent extends Event implements IModBusEvent {

    /**
     * @param id       a unique id, usually {@code <modid>:<name>}; shows up in the log
     * @param provider answers who a player's teammates are
     */
    public void register(String id, TeamProvider provider) {
        TeamProviders.register(id, provider);
        AddonRegistrations.record(ApiOwners.active(), id, ApiExtension.TEAM_PROVIDER);
    }
}
