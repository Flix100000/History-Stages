package net.bananemdnsa.historystages.platform;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.BuildCreativeModeTabContentsEvent;
import net.bananemdnsa.historystages.platform.event.RegisterCommandsEvent;
import net.bananemdnsa.historystages.platform.event.entity.player.AttackEntityEvent;
import net.bananemdnsa.historystages.platform.event.entity.player.PlayerEvent;
import net.bananemdnsa.historystages.platform.event.entity.player.PlayerInteractEvent;
import net.bananemdnsa.historystages.platform.event.entity.living.LivingDeathEvent;
import net.bananemdnsa.historystages.platform.event.level.BlockEvent;
import net.bananemdnsa.historystages.platform.event.level.LevelEvent;
import net.bananemdnsa.historystages.platform.event.server.ServerStoppingEvent;
import net.bananemdnsa.historystages.platform.event.tick.PlayerTickEvent;
import net.bananemdnsa.historystages.platform.event.tick.ServerTickEvent;
import net.bananemdnsa.historystages.util.ServerHolder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

/**
 * Where the mod's events come from on this loader.
 *
 * <p>Fabric has a callback for a good half of them, and those are raised here. The rest have no
 * callback at all and are raised from mixins written for them, which sit in {@code mixin/event}.
 * Splitting it that way keeps the mixins down to the cases that genuinely need bytecode.
 *
 * <p>Translating the answer back is the part worth reading. A cancelled event becomes a FAIL
 * result, which is what stops the interaction; PASS lets vanilla carry on. Returning SUCCESS
 * instead of PASS would swallow the interaction even when no lock applied.
 */
public final class EventSources {

    private EventSources() {}

    public static void register() {
        lifecycle();
        interaction();
        world();
        creativeTabs();
    }

    private static void lifecycle() {
        // The holder is what the rest of the mod reaches the server through, so it is set before
        // anything else can ask, and cleared while the server is still there to be written out.
        ServerLifecycleEvents.SERVER_STARTED.register(ServerHolder::set);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            EventBus.post(new ServerStoppingEvent(server));
            ServerHolder.set(null);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            EventBus.post(new ServerTickEvent.Post(server));
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                EventBus.post(new PlayerTickEvent.Post(player));
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                EventBus.post(new PlayerEvent.PlayerLoggedInEvent(handler.getPlayer())));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                EventBus.post(new PlayerEvent.PlayerLoggedOutEvent(handler.getPlayer())));

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
                EventBus.post(new PlayerEvent.PlayerChangedDimensionEvent(
                        player, origin.dimension(), destination.dimension())));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                EventBus.post(new RegisterCommandsEvent(dispatcher)));
    }

    private static void interaction() {
        // Right-clicking a block is deliberately absent: its answer has three states and a
        // callback has two, so it is raised from a mixin instead. See mixin/event.

        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            PlayerInteractEvent.LeftClickBlock event =
                    EventBus.post(new PlayerInteractEvent.LeftClickBlock(player, pos, direction));
            return event.isCanceled() ? InteractionResult.FAIL : InteractionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, level, hand) -> {
            PlayerInteractEvent.RightClickItem event =
                    EventBus.post(new PlayerInteractEvent.RightClickItem(player, hand));
            ItemStack held = player.getItemInHand(hand);
            return event.isCanceled()
                    ? InteractionResultHolder.fail(held)
                    : InteractionResultHolder.pass(held);
        });

        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            PlayerInteractEvent.EntityInteract event =
                    EventBus.post(new PlayerInteractEvent.EntityInteract(player, hand, entity));
            return event.isCanceled() ? InteractionResult.FAIL : InteractionResult.PASS;
        });

        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            AttackEntityEvent event = EventBus.post(new AttackEntityEvent(player, entity));
            return event.isCanceled() ? InteractionResult.FAIL : InteractionResult.PASS;
        });
    }

    private static void world() {
        // BEFORE rather than AFTER: a lock has to keep the block standing, not react to its loss.
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            BlockEvent.BreakEvent event =
                    EventBus.post(new BlockEvent.BreakEvent(level, pos, state, player));
            return !event.isCanceled();
        });

        ServerWorldEvents.LOAD.register((server, level) ->
                EventBus.post(new LevelEvent.Load(level)));

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) ->
                EventBus.post(new LivingDeathEvent(entity, source)));
    }

    /**
     * The creative tabs this mod adds to. NeoForge fires one event for every tab and lets the
     * handler pick; here each tab is subscribed to separately, so the same handler is reached with
     * the tab it asked about.
     */
    private static void creativeTabs() {
        for (ResourceKey<CreativeModeTab> tab : java.util.List.of(
                CreativeModeTabs.FUNCTIONAL_BLOCKS, CreativeModeTabs.INGREDIENTS)) {
            ItemGroupEvents.modifyEntriesEvent(tab).register(entries ->
                    EventBus.post(new BuildCreativeModeTabContentsEvent(tab, entries::accept)));
        }
    }
}
