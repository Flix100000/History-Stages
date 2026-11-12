package net.bananemdnsa.historystages.neoforge.events;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
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
        Bridges.listen(bus, ExplosionEvent.Detonate.class, LevelBridge::detonate);
        Bridges.listen(bus, LevelEvent.Load.class, LevelBridge::levelLoad);
        Bridges.listen(bus, LevelEvent.PotentialSpawns.class, LevelBridge::potentialSpawns);
        Bridges.listen(bus, ServerStoppingEvent.class, LevelBridge::serverStopping);
        Bridges.listen(bus, ServerTickEvent.Post.class, LevelBridge::serverTickPost);
        Bridges.listen(bus, RegisterCommandsEvent.class, LevelBridge::registerCommands);
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, LevelBridge::creativeTabContents);
    }

    /**
     * NeoForge 26.2 renamed {@code BlockEvent.BreakEvent} to {@code BreakBlockEvent} and fires it on
     * the client as well. A cancel on the server also resends the block, so the client does not
     * keep showing a hole when only the server-side handler said no.
     */
    private static void blockBreak(BreakBlockEvent event) {
        if (!Bridges.wanted(net.bananemdnsa.historystages.platform.event.level.BlockEvent.BreakEvent.class)) {
            return;
        }
        boolean wasCanceled = event.isCanceled();
        Bridges.post(event, new net.bananemdnsa.historystages.platform.event.level.BlockEvent.BreakEvent(
                event.getLevel(), event.getPos(), event.getState(), event.getPlayer()));
        if (event.isCanceled() && !wasCanceled && !event.getLevel().isClientSide()) {
            event.setNotifyClient(true);
        }
    }

    /** Also hears {@code EntityMultiPlaceEvent}, which is a subclass, so a bed or door is covered. */
    private static void entityPlace(BlockEvent.EntityPlaceEvent event) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.level.BlockEvent.EntityPlaceEvent.class)) {
            Bridges.post(event, new net.bananemdnsa.historystages.platform.event.level.BlockEvent.EntityPlaceEvent(
                    event.getLevel(), event.getPos(), event.getPlacedBlock(), event.getEntity()));
        }
    }

    /** The block list is the live one: a handler removes protected blocks from it in place. */
    private static void detonate(ExplosionEvent.Detonate event) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.level.ExplosionEvent.Detonate.class)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.level.ExplosionEvent.Detonate(
                    event.getLevel(), event.getExplosion(), event.getAffectedBlocks()));
        }
    }

    private static void levelLoad(LevelEvent.Load event) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.level.LevelEvent.Load.class)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.level.LevelEvent.Load(event.getLevel()));
        }
    }

    /**
     * NeoForge only hands out a read-only view and takes changes through add and remove, so the
     * handlers get a copy and the difference is applied afterwards.
     */
    private static void potentialSpawns(LevelEvent.PotentialSpawns event) {
        if (!Bridges.wanted(net.bananemdnsa.historystages.platform.event.level.LevelEvent.PotentialSpawns.class)) {
            return;
        }
        List<Weighted<MobSpawnSettings.SpawnerData>> before = List.copyOf(event.getSpawnerDataList());
        List<Weighted<MobSpawnSettings.SpawnerData>> after = new ArrayList<>(before);
        EventBus.post(new net.bananemdnsa.historystages.platform.event.level.LevelEvent.PotentialSpawns(
                event.getLevel(), event.getMobCategory(), event.getPos(), after));

        List<Weighted<MobSpawnSettings.SpawnerData>> added = new ArrayList<>(after);
        for (Weighted<MobSpawnSettings.SpawnerData> entry : before) {
            if (!added.remove(entry)) {
                event.removeSpawnerData(entry);
            }
        }
        added.forEach(event::addSpawnerData);
    }

    private static void serverStopping(ServerStoppingEvent event) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.server.ServerStoppingEvent.class)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.server.ServerStoppingEvent(event.getServer()));
        }
    }

    private static void serverTickPost(ServerTickEvent.Post event) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.tick.ServerTickEvent.Post.class)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.tick.ServerTickEvent.Post(event.getServer()));
        }
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.RegisterCommandsEvent.class)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.RegisterCommandsEvent(event.getDispatcher()));
        }
    }

    private static void creativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.BuildCreativeModeTabContentsEvent.class)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.BuildCreativeModeTabContentsEvent(
                    event.getTabKey(), event::accept));
        }
    }
}
