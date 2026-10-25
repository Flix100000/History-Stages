package net.bananemdnsa.historystages.data.relock;

import net.minecraftforge.common.MinecraftForge;
import net.bananemdnsa.historystages.data.saveddata.LostStagesData;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.SyncLostStagesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Stages lost for good through a lock trigger with {@code re_unlockable: false}. Pedestal,
 * auto-triggers, scripts and FTB rewards refuse them; admin commands and the editor go through
 * and clear the mark.
 */
public final class LostStages {

    private LostStages() {}

    public static boolean isLostGlobal(String stageId, ServerLevel level) {
        return LostStagesData.get(level).isLostGlobal(stageId);
    }

    public static boolean isLostIndividual(String stageId, UUID player, ServerLevel level) {
        return player != null && LostStagesData.get(level).isLostIndividual(player, stageId);
    }

    public static void markGlobal(String stageId, ServerLevel level) {
        if (LostStagesData.get(level).markGlobal(stageId)) syncAll(level);
    }

    public static void markIndividual(String stageId, ServerPlayer player) {
        if (LostStagesData.get(player.serverLevel()).markIndividual(player.getUUID(), stageId)) syncTo(player);
    }

    /**
     * Posts {@code StageEvent.LostForGood}. Separate from the marks because the lock trigger marks
     * before it relocks and takes the mark back if the relock fails; the event must only go out
     * once the loss has really happened.
     */
    public static void announce(String stageId, @org.jetbrains.annotations.Nullable UUID player) {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.bananemdnsa.historystages.api.stage.StageEvent.LostForGood(
                stageId, displayName(stageId, player != null), player));
    }

    private static String displayName(String stageId, boolean individual) {
        var entry = individual
                ? net.bananemdnsa.historystages.data.StageManager.getIndividualStages().get(stageId)
                : net.bananemdnsa.historystages.data.StageManager.getStages().get(stageId);
        return entry != null ? entry.getDisplayName() : stageId;
    }

    public static void clearGlobal(String stageId, ServerLevel level) {
        if (LostStagesData.get(level).clearGlobal(stageId)) syncAll(level);
    }

    public static void clearIndividual(String stageId, ServerPlayer player) {
        if (LostStagesData.get(player.serverLevel()).clearIndividual(player.getUUID(), stageId)) syncTo(player);
    }

    public static void syncTo(ServerPlayer player) {
        LostStagesData data = LostStagesData.get(player.serverLevel());
        PacketHandler.sendLostStagesToPlayer(
                new SyncLostStagesPacket(data.globalSnapshot(), data.individualSnapshot(player.getUUID())), player);
    }

    /** For bulk paths that clear on {@link LostStagesData} directly and sync once afterwards. */
    public static void syncAll(ServerLevel level) {
        if (level.getServer() == null) return;
        level.getServer().getPlayerList().getPlayers().forEach(LostStages::syncTo);
    }
}
