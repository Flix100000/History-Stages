package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.api.stage.StageEvent;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.stage.StageStateView;
import net.bananemdnsa.historystages.api.stage.StageStates;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.lock.engine.StageLocks;
import net.bananemdnsa.historystages.data.logic.StageLogic;
import net.bananemdnsa.historystages.data.logic.StageLogicGate;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.LogicPendingRevocations;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.EditorFeedbackPacket;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Carries out "revoke when" blocks.
 *
 * <p>Listens to the stage events every unlock and relock path already posts, so no path can slip
 * past it. For each change it asks the revoke blocks that mention the changed stage whether their
 * condition was false before and is true now; those fire, once, and take their stage away through
 * the ordinary relock path. A revocation is itself a stage change and runs through here again,
 * which is how chains happen — and since revocations only ever remove stages, every chain ends.
 *
 * <p>The state before the change is the state after it with that one stage switched back, built
 * explicitly rather than read from a cache that may or may not have caught up yet.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID)
public final class StageLogicRevocationHandler {

    private StageLogicRevocationHandler() {}

    // ---- admin reporting ---------------------------------------------------------------

    /** Revocations counted while an admin action runs: stage id → how many players (or -1 = world). */
    private static final ThreadLocal<Map<String, Integer>> REPORT = new ThreadLocal<>();

    /**
     * Runs an admin action and tells the admin what it revoked through logic blocks. Commands and
     * the editor's toggles wrap their changes in this.
     */
    public static <T> T reportTo(@Nullable ServerPlayer admin, Supplier<T> action) {
        if (admin == null || REPORT.get() != null) return action.get();
        Map<String, Integer> report = new LinkedHashMap<>();
        REPORT.set(report);
        try {
            return action.get();
        } finally {
            REPORT.remove();
            if (!report.isEmpty()) {
                List<String> parts = new ArrayList<>();
                report.forEach((stage, count) -> parts.add(count < 0
                        ? StageLogicGate.nameForAnyScope(stage, null) + " (global)"
                        : StageLogicGate.nameForAnyScope(stage, null) + " (" + count + ")"));
                PacketHandler.sendEditorFeedback(EditorFeedbackPacket.info(
                        "toast.historystages.revoked_admin.title",
                        "toast.historystages.revoked_admin.message", String.join(", ", parts)), admin);
            }
        }
    }

    private static void count(String stageId, boolean world) {
        Map<String, Integer> report = REPORT.get();
        if (report == null) return;
        report.merge(stageId, world ? -1 : 1, (a, b) -> a < 0 ? a : a + b);
    }

    // ---- the player an individual change is for ------------------------------------------

    /** The player object behind the individual stage event being handled, when the poster had one. */
    private static final ThreadLocal<ServerPlayer> ACTOR = new ThreadLocal<>();

    /** Posts an individual stage event with the player known, restoring whatever was known before. */
    public static void withActor(ServerPlayer player, Runnable post) {
        ServerPlayer previous = ACTOR.get();
        ACTOR.set(player);
        try {
            post.run();
        } finally {
            if (previous == null) ACTOR.remove(); else ACTOR.set(previous);
        }
    }

    /** The online player for this UUID: the one the event was posted for, else the player list. */
    @Nullable
    private static ServerPlayer online(MinecraftServer server, UUID uuid) {
        ServerPlayer actor = ACTOR.get();
        if (actor != null && actor.getUUID().equals(uuid)) return actor;
        return server.getPlayerList().getPlayer(uuid);
    }

    // ---- stage events -------------------------------------------------------------------

    @SubscribeEvent
    public static void onUnlocked(StageEvent.Unlocked event) {
        globalChanged(event.getStageId(), true);
    }

    @SubscribeEvent
    public static void onLocked(StageEvent.Locked event) {
        globalChanged(event.getStageId(), false);
    }

    @SubscribeEvent
    public static void onIndividualUnlocked(StageEvent.IndividualUnlocked event) {
        individualChanged(event.getStageId(), event.getPlayerUUID(), true);
    }

    @SubscribeEvent
    public static void onIndividualLocked(StageEvent.IndividualLocked event) {
        individualChanged(event.getStageId(), event.getPlayerUUID(), false);
    }

    /** A global stage changed: global stages may react, and every player's individual stages. */
    static void globalChanged(String changed, boolean nowUnlocked) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel level = server.overworld();

        StageStateView globalAfter = StageLogic.withChange(StageLocks.serverGlobal(), changed, nowUnlocked);
        StageStateView globalBefore = StageLogic.withChange(StageLocks.serverGlobal(), changed, !nowUnlocked);

        // Global stages the world owns.
        for (Map.Entry<String, StageEntry> e : List.copyOf(StageManager.getStages().entrySet())) {
            String target = e.getKey();
            if (!mentions(e.getValue(), StageScope.GLOBAL, StageScope.GLOBAL, changed)) continue;
            if (!StageData.get(level).hasStage(target)) continue;
            if (!StageLogic.revokeFires(e.getValue().getLogicBlocks(), StageScope.GLOBAL,
                    globalBefore, StageStateView.NONE_UNLOCKED, globalAfter, StageStateView.NONE_UNLOCKED)) continue;
            DebugLogger.runtime("Stage Logic", "Revoking global stage '" + target + "' because '" + changed
                    + "' is now " + (nowUnlocked ? "unlocked" : "locked") + ".");
            count(target, true);
            if (StageStates.relockGlobal(target, level)) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    toast(player, target, false, changed, false, nowUnlocked);
                }
            }
        }

        // Individual stages every player may own, online or not.
        IndividualStageData individual = IndividualStageData.get(level);
        for (Map.Entry<String, StageEntry> e : List.copyOf(StageManager.getIndividualStages().entrySet())) {
            String target = e.getKey();
            if (!mentions(e.getValue(), StageScope.INDIVIDUAL, StageScope.GLOBAL, changed)) continue;
            for (UUID uuid : Set.copyOf(individual.getAllPlayersWithStage(target))) {
                StageStateView own = StageLocks.serverIndividual(uuid);
                if (!StageLogic.revokeFires(e.getValue().getLogicBlocks(), StageScope.INDIVIDUAL,
                        globalBefore, own, globalAfter, own)) continue;
                revokeIndividual(server, uuid, target, changed, false, nowUnlocked);
            }
        }
    }

    /** One player's individual stage changed: only that player's individual stages can react. */
    static void individualChanged(String changed, UUID uuid, boolean nowUnlocked) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        IndividualStageData individual = IndividualStageData.get(server.overworld());

        StageStateView global = StageLocks.serverGlobal();
        StageStateView after = StageLogic.withChange(StageLocks.serverIndividual(uuid), changed, nowUnlocked);
        StageStateView before = StageLogic.withChange(StageLocks.serverIndividual(uuid), changed, !nowUnlocked);

        for (Map.Entry<String, StageEntry> e : List.copyOf(StageManager.getIndividualStages().entrySet())) {
            String target = e.getKey();
            if (target.equals(changed)) continue;
            if (!mentions(e.getValue(), StageScope.INDIVIDUAL, StageScope.INDIVIDUAL, changed)) continue;
            if (!individual.hasStage(uuid, target)) continue;
            if (!StageLogic.revokeFires(e.getValue().getLogicBlocks(), StageScope.INDIVIDUAL,
                    global, before, global, after)) continue;
            revokeIndividual(server, uuid, target, changed, true, nowUnlocked);
        }
    }

    private static boolean mentions(StageEntry entry, StageScope owner, StageScope of, String changed) {
        return entry.hasLogic() && StageLogic.revokeReferences(entry.getLogicBlocks(), owner, of).contains(changed);
    }

    private static void revokeIndividual(MinecraftServer server, UUID uuid, String target, String changed,
                                         boolean changedIndividual, boolean nowUnlocked) {
        count(target, false);
        ServerPlayer player = online(server, uuid);
        if (player == null) {
            // Offline: owed, and carried out at the next login.
            LogicPendingRevocations.get(server.overworld()).add(uuid, target);
            DebugLogger.runtime("Stage Logic", "Queued revocation of '" + target + "' for offline player " + uuid + ".");
            return;
        }
        DebugLogger.runtime("Stage Logic", player.getName().getString(),
                "Revoking individual stage '" + target + "' because '" + changed + "' is now "
                        + (nowUnlocked ? "unlocked" : "locked") + ".");
        if (StageStates.relockIndividual(target, player)) {
            toast(player, target, true, changed, changedIndividual, nowUnlocked);
        }
    }

    /** What a login owes: revocations that fired while the player was away. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Set<String> owed = LogicPendingRevocations.get(player.serverLevel()).take(player.getUUID());
        for (String target : owed) {
            if (!IndividualStageData.get(player.serverLevel()).hasStage(player.getUUID(), target)) continue;
            if (StageStates.relockIndividual(target, player)) toast(player, target, true, null, false, false);
        }
    }

    // ---- feedback -----------------------------------------------------------------------

    /**
     * "‹Stage› was revoked, because ‹X› is now unlocked/locked." Plain under PLAIN, and when the
     * cause is no longer known (a revocation owed from while the player was offline).
     */
    private static void toast(ServerPlayer player, String target, boolean targetIndividual,
                              @Nullable String cause, boolean causeIndividual, boolean causeUnlocked) {
        String name = StageLogicGate.nameFor(target, targetIndividual, player.getUUID());
        String override = Config.VISUAL.msgStageRevoked.get();
        EditorFeedbackPacket packet;
        if (override != null && !override.isEmpty()) {
            packet = EditorFeedbackPacket.error("toast.historystages.revoked.title", "message.historystages.raw",
                    override.replace("{stage}", name).replace('&', '§'));
        } else if (cause == null || Config.VISUAL.blockedDisplay.get() != Config.Visual.BlockedDisplay.REASON) {
            packet = EditorFeedbackPacket.error("toast.historystages.revoked.title",
                    "toast.historystages.revoked.plain", name);
        } else {
            packet = EditorFeedbackPacket.error("toast.historystages.revoked.title",
                    causeUnlocked ? "toast.historystages.revoked.because_unlocked"
                            : "toast.historystages.revoked.because_locked",
                    name, StageLogicGate.nameFor(cause, causeIndividual, player.getUUID()));
        }
        PacketHandler.sendEditorFeedback(packet, player);
    }
}
