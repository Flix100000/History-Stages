package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.api.stage.StageEvent;
import net.bananemdnsa.historystages.data.relock.ConditionalStageManager;
import net.bananemdnsa.historystages.data.relock.LockTriggerManager;
import net.bananemdnsa.historystages.data.relock.LostStages;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Housekeeping for re-locking that hangs off events rather than off the tick. */
@EventBusSubscriber(modid = HistoryStages.MOD_ID)
public final class RelockEventHandler {

    private RelockEventHandler() {}

    // Every unlock path posts these (pedestal, auto, command, script, FTB), so lock progress
    // starts from zero no matter how the stage came back.
    @SubscribeEvent
    public static void onUnlocked(StageEvent.Unlocked event) {
        ServerLevel overworld = overworld();
        if (overworld == null) return;
        LockTriggerManager.clearProgress(event.getStageId(), false, null, overworld);
    }

    @SubscribeEvent
    public static void onIndividualUnlocked(StageEvent.IndividualUnlocked event) {
        ServerLevel overworld = overworld();
        if (overworld == null) return;
        LockTriggerManager.clearProgress(event.getStageId(), true, event.getPlayerUUID(), overworld);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        LostStages.syncTo(player);
        // Do not wait up to a second: a player who logs in outside the Nether must not keep the
        // Nether stage they logged out with.
        ConditionalStageManager.evaluateNow(player);
    }

    private static ServerLevel overworld() {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server != null ? server.overworld() : null;
    }
}
