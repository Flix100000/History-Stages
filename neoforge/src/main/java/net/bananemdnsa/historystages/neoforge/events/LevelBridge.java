package net.bananemdnsa.historystages.neoforge.events;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.bus.EventPriority;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Blocks, explosions, levels, the server itself, and the two registration events. */
public final class LevelBridge {

    private LevelBridge() {}

    public static void register(IEventBus bus, IEventBus modBus) {
        Bridges.listenCancellable(bus, BreakBlockEvent.class, LevelBridge::blockBreak);
        Bridges.listenCancellable(bus, BlockEvent.EntityPlaceEvent.class, LevelBridge::entityPlace);
        Bridges.listenCancellable(bus, BlockDropsEvent.class, LevelBridge::blockDrops);
        Bridges.listen(bus, ExplosionEvent.Detonate.class, LevelBridge::detonate);
        Bridges.listen(bus, LevelEvent.Load.class, LevelBridge::levelLoad);
        Bridges.listen(bus, LevelEvent.PotentialSpawns.class, LevelBridge::potentialSpawns);
        Bridges.listen(bus, ServerStoppingEvent.class, LevelBridge::serverStopping);
        Bridges.listen(bus, ServerTickEvent.Post.class, LevelBridge::serverTickPost);
        Bridges.listen(bus, RegisterCommandsEvent.class, LevelBridge::registerCommands);
        Bridges.listen(modBus, BuildCreativeModeTabContentsEvent.class, LevelBridge::creativeTabContents);
    }

    /**
     * NeoForge 26.2 renamed {@code BlockEvent.BreakEvent} to {@code BreakBlockEvent} and fires it on
     * the client as well. The platform event keeps the old server-only meaning, so client-side
     * firings are not passed on. A cancel on the server also resends the block, so the client does
     * not keep showing the hole it predicted.
     */
    private static void blockBreak(BreakBlockEvent event, EventPriority priority) {
        if (event.getLevel().isClientSide()
                || !Bridges.wanted(net.bananemdnsa.historystages.platform.event.level.BlockEvent.BreakEvent.class, priority)) {
            return;
        }
        boolean wasCanceled = event.isCanceled();
        Bridges.post(event, new net.bananemdnsa.historystages.platform.event.level.BlockEvent.BreakEvent(
                event.getLevel(), event.getPos(), event.getState(), event.getPlayer()), priority);
        if (event.isCanceled() && !wasCanceled) {
            event.setNotifyClient(true);
        }
    }

    /** The drop list is NeoForge's own, so handlers edit what actually spawns. */
    private static void blockDrops(BlockDropsEvent event, EventPriority priority) {
        if (!Bridges.wanted(net.bananemdnsa.historystages.platform.event.level.BlockDropsEvent.class, priority)) {
            return;
        }
        var platform = Bridges.post(event, new net.bananemdnsa.historystages.platform.event.level.BlockDropsEvent(
                event.getLevel(), event.getPos(), event.getState(), event.getBlockEntity(), event.getDrops(),
                event.getBreaker(), event.getTool(), event.getDroppedExperience()), priority);
        event.setDroppedExperience(platform.getDroppedExperience());
    }

    /** Also hears {@code EntityMultiPlaceEvent}, which is a subclass, so a bed or door is covered. */
    private static void entityPlace(BlockEvent.EntityPlaceEvent event, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.level.BlockEvent.EntityPlaceEvent.class, priority)) {
            Bridges.post(event, new net.bananemdnsa.historystages.platform.event.level.BlockEvent.EntityPlaceEvent(
                    event.getLevel(), event.getPos(), event.getPlacedBlock(), event.getEntity()), priority);
        }
    }

    /** The block list is the live one: a handler removes protected blocks from it in place. */
    private static void detonate(ExplosionEvent.Detonate event, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.level.ExplosionEvent.Detonate.class, priority)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.level.ExplosionEvent.Detonate(
                    event.getLevel(), event.getExplosion(), event.getAffectedBlocks()), priority);
        }
    }

    private static void levelLoad(LevelEvent.Load event, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.level.LevelEvent.Load.class, priority)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.level.LevelEvent.Load(event.getLevel()), priority);
        }
    }

    /**
     * NeoForge only hands out a read-only view and takes changes through add and remove, so the
     * handlers get a copy and the difference is applied afterwards.
     */
    private static void potentialSpawns(LevelEvent.PotentialSpawns event, EventPriority priority) {
        if (!Bridges.wanted(net.bananemdnsa.historystages.platform.event.level.LevelEvent.PotentialSpawns.class, priority)) {
            return;
        }
        List<Weighted<MobSpawnSettings.SpawnerData>> before = List.copyOf(event.getSpawnerDataList());
        List<Weighted<MobSpawnSettings.SpawnerData>> after = new ArrayList<>(before);
        EventBus.post(new net.bananemdnsa.historystages.platform.event.level.LevelEvent.PotentialSpawns(
                event.getLevel(), event.getMobCategory(), event.getPos(), after), priority);

        List<Weighted<MobSpawnSettings.SpawnerData>> added = new ArrayList<>(after);
        for (Weighted<MobSpawnSettings.SpawnerData> entry : before) {
            if (!added.remove(entry)) {
                event.removeSpawnerData(entry);
            }
        }
        added.forEach(event::addSpawnerData);
    }

    private static void serverStopping(ServerStoppingEvent event, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.server.ServerStoppingEvent.class, priority)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.server.ServerStoppingEvent(event.getServer()), priority);
        }
    }

    private static void serverTickPost(ServerTickEvent.Post event, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.tick.ServerTickEvent.Post.class, priority)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.tick.ServerTickEvent.Post(event.getServer()), priority);
        }
    }

    private static void registerCommands(RegisterCommandsEvent event, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.RegisterCommandsEvent.class, priority)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.RegisterCommandsEvent(event.getDispatcher()), priority);
        }
    }

    private static void creativeTabContents(BuildCreativeModeTabContentsEvent event, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.BuildCreativeModeTabContentsEvent.class, priority)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.BuildCreativeModeTabContentsEvent(
                    event.getTabKey(), event::accept), priority);
        }
    }
}
