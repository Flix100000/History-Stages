package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.data.team.TeamStageSharing;
import net.bananemdnsa.historystages.events.lock.AttributeRefreshHandler;
import net.bananemdnsa.historystages.events.lock.BiomeLockHandler;
import net.bananemdnsa.historystages.events.lock.BlockLockHandler;
import net.bananemdnsa.historystages.events.lock.DimensionLockHandler;
import net.bananemdnsa.historystages.events.lock.EnchantmentLockHandler;
import net.bananemdnsa.historystages.events.lock.EntityItemLockHandler;
import net.bananemdnsa.historystages.events.lock.FluidPickupLockHandler;
import net.bananemdnsa.historystages.events.lock.FluidPlaceLockHandler;
import net.bananemdnsa.historystages.events.lock.InteractionLockHandler;
import net.bananemdnsa.historystages.events.lock.ItemUseLockHandler;
import net.bananemdnsa.historystages.events.lock.MobLockHandler;
import net.bananemdnsa.historystages.events.lock.MobLootLockHandler;
import net.bananemdnsa.historystages.events.lock.MobSpawnLockHandler;
import net.bananemdnsa.historystages.events.lock.StructureLockHandler;
import net.bananemdnsa.historystages.events.lock.ZoneBarrierHandler;
import net.bananemdnsa.historystages.events.lock.ZoneLockHandler;
import net.bananemdnsa.historystages.events.lock.ZoneSpawnHandler;
import net.bananemdnsa.historystages.platform.bus.EventBus;

import java.util.List;

/**
 * The {@code @EventBusSubscriber} classes, written out because nothing scans for the annotation
 * on the platform bus. A handler missing here fails silently: the rule just never applies.
 *
 * <p>Client handlers are in {@code client.ClientHandlers}, so a dedicated server never loads them.
 * {@code DisguiseBreakHandler} waits in port-pending for the HarvestCheck/BlockDrops facade.
 */
public final class Handlers {

    private Handlers() {}

    public static final List<Class<?>> COMMON = List.of(
            LecternScrollHandler.class,
            RelockEventHandler.class,
            StageDefinitionsEventBridge.class,
            StageDeathLossHandler.class,
            StageLogicRevocationHandler.class,
            ZoneMarkingHandler.class,
            AttributeRefreshHandler.class,
            BiomeLockHandler.class,
            BlockLockHandler.class,
            DimensionLockHandler.class,
            EnchantmentLockHandler.class,
            EntityItemLockHandler.class,
            FluidPickupLockHandler.class,
            FluidPlaceLockHandler.class,
            InteractionLockHandler.class,
            ItemUseLockHandler.class,
            MobLockHandler.class,
            MobLootLockHandler.class,
            MobSpawnLockHandler.class,
            StructureLockHandler.class,
            ZoneBarrierHandler.class,
            ZoneLockHandler.class,
            ZoneSpawnHandler.class,
            TeamStageSharing.class);

    public static void registerCommon() {
        for (Class<?> handler : COMMON) {
            EventBus.register(handler);
        }
    }
}
