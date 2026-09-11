package net.bananemdnsa.historystages.events.lock;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.lock.engine.StageLocks;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;

/**
 * The ways into a barrier zone that do not involve walking.
 *
 * <p>A barrier that only reacts to footsteps is a sieve, and this is the half of it that cannot
 * live in the tick: by the time a tick sees the player, the trip has already happened.
 *
 * <p>Elytra and other fast movement are handled in {@link ZoneLockHandler} instead, because the
 * check there is "where were you last tick", and that is the tick's own business.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID)
public final class ZoneBarrierHandler {

    private ZoneBarrierHandler() {}

    /**
     * An ender pearl aimed into a barrier zone simply fails.
     *
     * <p>Letting it land and pushing back afterwards would still have put the player inside for a
     * moment — long enough to see what is in there, which is usually the whole point of the zone.
     */
    @SubscribeEvent
    public static void onEnderPearl(EntityTeleportEvent.EnderPearl event) {
        if (!StageLocks.engine().anyZoneLocks()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative() || player.isSpectator()) return;

        if (ZoneLockHandler.barrierCovers(player, event.getTargetX(), event.getTargetY(),
                event.getTargetZ())) {
            event.setCanceled(true);
        }
    }
}
