package net.bananemdnsa.historystages.events.lock;

import java.util.UUID;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.api.stage.StageEvent;
import net.bananemdnsa.historystages.util.lock.HeldAttributeRefresher;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.bananemdnsa.historystages.platform.bus.SubscribeEvent;
import net.bananemdnsa.historystages.platform.bus.EventBusSubscriber;
import net.bananemdnsa.historystages.util.ServerHolder;

/**
 * Forces an attribute re-evaluation when a stage's lock state changes, so the
 * {@link net.bananemdnsa.historystages.mixin.AttributeLockMixin} suppression takes effect
 * immediately on items the player is already holding.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID)
public final class AttributeRefreshHandler {

    private AttributeRefreshHandler() {}

    @SubscribeEvent
    public static void onUnlocked(StageEvent.Unlocked event) {
        refreshAllOnline();
    }

    @SubscribeEvent
    public static void onLocked(StageEvent.Locked event) {
        refreshAllOnline();
    }

    @SubscribeEvent
    public static void onIndividualUnlocked(StageEvent.IndividualUnlocked event) {
        refreshPlayer(event.getPlayerUUID());
    }

    @SubscribeEvent
    public static void onIndividualLocked(StageEvent.IndividualLocked event) {
        refreshPlayer(event.getPlayerUUID());
    }

    private static void refreshAllOnline() {
        MinecraftServer server = ServerHolder.get();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            HeldAttributeRefresher.refresh(player);
        }
    }

    private static void refreshPlayer(UUID uuid) {
        MinecraftServer server = ServerHolder.get();
        if (server == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) HeldAttributeRefresher.refresh(player);
    }
}
