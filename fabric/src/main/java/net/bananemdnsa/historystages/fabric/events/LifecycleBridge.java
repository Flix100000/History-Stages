package net.bananemdnsa.historystages.fabric.events;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.BuildCreativeModeTabContentsEvent;
import net.bananemdnsa.historystages.platform.event.RegisterCommandsEvent;
import net.bananemdnsa.historystages.platform.event.entity.living.LivingEquipmentChangeEvent;
import net.bananemdnsa.historystages.platform.event.entity.living.LivingIncomingDamageEvent;
import net.bananemdnsa.historystages.platform.event.entity.player.PlayerEvent;
import net.bananemdnsa.historystages.platform.event.level.LevelEvent;
import net.bananemdnsa.historystages.platform.event.server.ServerStoppingEvent;
import net.bananemdnsa.historystages.platform.event.tick.ServerTickEvent;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Server lifecycle, connections, levels, damage, equipment and creative tabs: everything with a
 * Fabric callback that is not a player click. One listener per priority, see {@link Phases}.
 */
public final class LifecycleBridge {

    private LifecycleBridge() {}

    public static void register() {
        Phases.listen(CommandRegistrationCallback.EVENT, priority -> (dispatcher, context, selection) -> {
            if (Phases.wanted(RegisterCommandsEvent.class, priority)) {
                EventBus.post(new RegisterCommandsEvent(dispatcher), priority);
            }
        });

        Phases.listen(ServerPlayConnectionEvents.JOIN, priority -> (handler, sender, server) -> {
            if (Phases.wanted(PlayerEvent.PlayerLoggedInEvent.class, priority)) {
                EventBus.post(new PlayerEvent.PlayerLoggedInEvent(handler.getPlayer()), priority);
            }
        });
        Phases.listen(ServerPlayConnectionEvents.DISCONNECT, priority -> (handler, server) -> {
            if (Phases.wanted(PlayerEvent.PlayerLoggedOutEvent.class, priority)) {
                EventBus.post(new PlayerEvent.PlayerLoggedOutEvent(handler.getPlayer()), priority);
            }
        });
        Phases.listen(ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL, priority -> (player, origin, destination) -> {
            if (Phases.wanted(PlayerEvent.PlayerChangedDimensionEvent.class, priority)) {
                EventBus.post(new PlayerEvent.PlayerChangedDimensionEvent(
                        player, origin.dimension(), destination.dimension()), priority);
            }
        });

        // Server levels only. NeoForge also fires Load for the client level; the one handler on
        // main (HistoryStages.onWorldLoad) is about the server.
        Phases.listen(ServerLevelEvents.LOAD, priority -> (server, level) -> {
            if (Phases.wanted(LevelEvent.Load.class, priority)) {
                EventBus.post(new LevelEvent.Load(level), priority);
            }
        });
        Phases.listen(ServerLifecycleEvents.SERVER_STOPPING, priority -> server -> {
            if (Phases.wanted(ServerStoppingEvent.class, priority)) {
                EventBus.post(new ServerStoppingEvent(server), priority);
            }
        });
        Phases.listen(ServerTickEvents.END_SERVER_TICK, priority -> server -> {
            if (Phases.wanted(ServerTickEvent.Post.class, priority)) {
                EventBus.post(new ServerTickEvent.Post(server), priority);
            }
        });

        // Fabric asks this in hurtServer after the invulnerability checks and before armour, the
        // same spot as NeoForge's LivingIncomingDamageEvent.
        Phases.listen(ServerLivingEntityEvents.ALLOW_DAMAGE, priority -> (entity, source, amount) -> {
            if (!Phases.wanted(LivingIncomingDamageEvent.class, priority)) {
                return true;
            }
            return !Phases.postCancellable(new LivingIncomingDamageEvent(entity, source), priority);
        });

        Phases.listen(ServerEntityEvents.EQUIPMENT_CHANGE, priority -> (entity, slot, from, to) -> {
            if (Phases.wanted(LivingEquipmentChangeEvent.class, priority)) {
                EventBus.post(new LivingEquipmentChangeEvent(entity, slot, from, to), priority);
            }
        });

        // Every tab, as on NeoForge, where the handler picks the tab it cares about.
        Phases.listen(CreativeModeTabEvents.MODIFY_OUTPUT_ALL, priority -> (tab, output) -> {
            if (!Phases.wanted(BuildCreativeModeTabContentsEvent.class, priority)) {
                return;
            }
            BuiltInRegistries.CREATIVE_MODE_TAB.getResourceKey(tab).ifPresent(key ->
                    EventBus.post(new BuildCreativeModeTabContentsEvent(key, output::accept), priority));
        });
    }
}
