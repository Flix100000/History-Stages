package net.bananemdnsa.historystages.data.team;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.api.stage.StageEvent;
import net.bananemdnsa.historystages.api.stage.StageStates;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.TeamSharePending;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Hands individual stages flagged {@code share_with_team} on to the rest of the player's team.
 *
 * <p>Hangs off {@link StageEvent} rather than {@link StageStates#unlockIndividual}: the pedestal
 * and the FTB Quests reward write the stage themselves and only post the event, so a hook on
 * StageStates would miss the two most common ways a stage is earned. Admin paths post the same
 * events and are told apart by wrapping them in {@link #withoutSharing}.
 *
 * <p>Teammates go through the normal unlock, so a stage that is lost for good or blocked by its
 * logic for one of them stays shut for that one.
 */
@Mod.EventBusSubscriber(modid = HistoryStages.MOD_ID)
public final class TeamStageSharing {

    /**
     * Depth of {@link #withoutSharing} on this thread. A counter rather than a flag so nesting
     * restores correctly. Everything below runs on the server thread, so a ThreadLocal is the
     * whole story.
     */
    private static final ThreadLocal<int[]> SUPPRESSED = ThreadLocal.withInitial(() -> new int[1]);

    private TeamStageSharing() {}

    /**
     * Runs {@code action} with sharing off. For admin unlocks and locks, which mean exactly the
     * player they name, and for the changes sharing makes itself — a stage that arrives through
     * the team must not be passed on again.
     */
    public static void withoutSharing(Runnable action) {
        withoutSharing(() -> {
            action.run();
            return null;
        });
    }

    public static <T> T withoutSharing(Supplier<T> action) {
        int[] depth = SUPPRESSED.get();
        depth[0]++;
        try {
            return action.get();
        } finally {
            depth[0]--;
        }
    }

    private static boolean suppressed() {
        return SUPPRESSED.get()[0] > 0;
    }

    /** True if unlocking or losing {@code stageId} reaches the rest of the team. */
    public static boolean isShared(String stageId) {
        StageEntry entry = StageManager.getIndividualStages().get(stageId);
        return entry != null && entry.isShareWithTeam() && entry.getMode().allowsTeamSharing();
    }

    // ---- events -------------------------------------------------------------------------

    // LOWEST so everything else has finished with the original player before teammates follow.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onIndividualUnlocked(StageEvent.IndividualUnlocked event) {
        if (suppressed() || !isShared(event.getStageId())) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        String stageId = event.getStageId();
        TeamSharePending pending = TeamSharePending.get(server.overworld());

        for (UUID mate : TeamProviders.teammates(server, event.getPlayerUUID())) {
            ServerPlayer player = server.getPlayerList().getPlayer(mate);
            if (player == null) {
                if (!IndividualStageData.get(server.overworld()).hasStage(mate, stageId)) {
                    pending.addGrant(mate, stageId);
                    DebugLogger.runtime("Team Share", "Queued '" + stageId + "' for offline teammate " + mate + ".");
                }
                continue;
            }
            if (withoutSharing(() -> StageStates.unlockIndividual(stageId, player))) {
                DebugLogger.runtime("Team Share", player.getName().getString(),
                        "Received '" + stageId + "' from teammate " + event.getPlayerUUID() + ".");
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onIndividualLocked(StageEvent.IndividualLocked event) {
        if (suppressed() || !Config.GAMEPLAY.teamShareLoss.get() || !isShared(event.getStageId())) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        String stageId = event.getStageId();
        TeamSharePending pending = TeamSharePending.get(server.overworld());

        for (UUID mate : TeamProviders.teammates(server, event.getPlayerUUID())) {
            ServerPlayer player = server.getPlayerList().getPlayer(mate);
            if (player == null) {
                // Also cancels a grant still owed from earlier, so the absent player ends up
                // where the team is now.
                pending.addLoss(mate, stageId);
                continue;
            }
            // A normal relock, never a permanent loss: the teammate did not trip the lock trigger.
            if (withoutSharing(() -> StageStates.relockIndividual(stageId, player))) {
                DebugLogger.runtime("Team Share", player.getName().getString(),
                        "Lost '" + stageId + "' together with teammate " + event.getPlayerUUID() + ".");
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();
        TeamSharePending pending = TeamSharePending.get(level);

        Set<String> losses = pending.takeLosses(player.getUUID());
        Set<String> grants = pending.takeGrants(player.getUUID());
        withoutSharing(() -> {
            for (String stageId : losses) {
                if (isShared(stageId)) StageStates.relockIndividual(stageId, player);
            }
            // Asked again here: the stage may have stopped sharing, or been deleted, meanwhile.
            for (String stageId : grants) {
                if (isShared(stageId)) StageStates.unlockIndividual(stageId, player);
            }
        });

        if (Config.GAMEPLAY.teamShareOnJoin.get()) catchUp(player);
    }

    /** A team provider reports that {@code player} joined a team. */
    public static void onJoinedTeam(ServerPlayer player) {
        if (Config.GAMEPLAY.teamShareOnJoin.get()) catchUp(player);
    }

    /** Hands {@code player} every shared stage any teammate already has, online or not. */
    static void catchUp(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        Set<UUID> mates = TeamProviders.teammates(server, player.getUUID());
        if (mates.isEmpty()) return;
        IndividualStageData data = IndividualStageData.get(player.serverLevel());

        withoutSharing(() -> {
            for (Map.Entry<String, StageEntry> e : Map.copyOf(StageManager.getIndividualStages()).entrySet()) {
                String stageId = e.getKey();
                if (!isShared(stageId) || data.hasStage(player.getUUID(), stageId)) continue;
                boolean teamHasIt = mates.stream().anyMatch(mate -> data.hasStage(mate, stageId));
                if (teamHasIt && StageStates.unlockIndividual(stageId, player)) {
                    DebugLogger.runtime("Team Share", player.getName().getString(),
                            "Caught up on '" + stageId + "' from the team.");
                }
            }
        });
    }
}
