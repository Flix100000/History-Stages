package net.bananemdnsa.historystages.platform;

import net.bananemdnsa.historystages.events.LecternScrollHandler;
import net.bananemdnsa.historystages.events.StageDeathLossHandler;
import net.bananemdnsa.historystages.events.ZoneMarkingHandler;
import net.bananemdnsa.historystages.events.lock.AttributeRefreshHandler;
import net.bananemdnsa.historystages.events.lock.BiomeLockHandler;
import net.bananemdnsa.historystages.events.lock.BlockLockHandler;
import net.bananemdnsa.historystages.events.lock.DimensionLockHandler;
import net.bananemdnsa.historystages.events.lock.EnchantmentLockHandler;
import net.bananemdnsa.historystages.events.lock.EntityItemLockHandler;
import net.bananemdnsa.historystages.events.lock.FluidPickupLockHandler;
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
 * The handler classes, named because nothing here can find them on its own.
 *
 * <p>NeoForge scans the jar for the annotation that marks them. There is no such scan on this
 * loader, so the list is written out — and a handler left off it fails in the quietest way there
 * is: no error, no warning, just a rule that never applies. {@code EventBusSubscriberCoverageTest}
 * walks the sources and compares them against this list for exactly that reason.
 *
 * <p>Two lists, because the client ones reach user interface classes that must never be loaded on
 * a dedicated server.
 */
public final class Handlers {

    private Handlers() {}

    /** Handlers that belong on both sides. */
    public static final List<Class<?>> COMMON = List.of(
            LecternScrollHandler.class,
            StageDeathLossHandler.class,
            ZoneMarkingHandler.class,
            AttributeRefreshHandler.class,
            BiomeLockHandler.class,
            BlockLockHandler.class,
            DimensionLockHandler.class,
            EnchantmentLockHandler.class,
            EntityItemLockHandler.class,
            FluidPickupLockHandler.class,
            InteractionLockHandler.class,
            ItemUseLockHandler.class,
            MobLockHandler.class,
            MobLootLockHandler.class,
            MobSpawnLockHandler.class,
            StructureLockHandler.class,
            ZoneBarrierHandler.class,
            ZoneLockHandler.class,
            ZoneSpawnHandler.class);

    public static void registerCommon() {
        for (Class<?> handler : COMMON) {
            EventBus.register(handler);
        }
    }
}
