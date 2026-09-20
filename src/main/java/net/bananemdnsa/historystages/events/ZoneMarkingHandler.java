package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.lock.ZoneSelection;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.SyncZoneSelectionPacket;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;

/**
 * Marking out zone corners with an ordinary item, WorldEdit-style.
 *
 * <p>No item of our own: the one to use is named in the config, so a pack author can pick
 * something they already carry. That only works because marking needs a sneak — without it the
 * item keeps its normal behaviour completely, so setting this to a pickaxe costs nothing.
 *
 * <p>Three conditions, all of them: permission level 2 (the same bar the editor sits behind), the
 * configured item in hand, and sneaking.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID)
public final class ZoneMarkingHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Resolved once per config value; an id nobody can resolve is logged once and remembered. */
    private static volatile String cachedId = null;
    private static volatile Item cachedItem = null;

    private ZoneMarkingHandler() {}

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!isMarking(event.getEntity(), event.getItemStack())) return;

        mark((ServerPlayer) event.getEntity(), 1, event.getPos());
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // Another handler already refused this interaction; nothing to add.
        if (event.getUseBlock() == TriState.FALSE) return;
        if (!isMarking(event.getEntity(), event.getItemStack())) return;

        mark((ServerPlayer) event.getEntity(), 2, event.getPos());
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        // The client predicted whatever the item normally does; a FAIL alone does not reliably
        // undo that. Same reasoning as the lock handlers.
        ((ServerPlayer) event.getEntity()).containerMenu.broadcastFullState();
    }

    /**
     * A plain right click at nothing discards the selection.
     *
     * <p>{@code RightClickItem} only fires when the click found neither block nor entity, so
     * pointing at the sky is the one gesture that cannot be an attempt to mark something.
     *
     * <p>Without the sneak on purpose. Sharing it with marking made the outcome depend on whether
     * the click happened to find a block: the same key press either set the second corner or threw
     * the whole selection away, which is as good as random while lining up a wall.
     *
     * <p>The cost is that the item loses its own right-click-at-air while a selection is standing.
     * Only then — with nothing marked the click is left alone, so an item picked for marking still
     * behaves normally the rest of the time.
     *
     * <p>Left click at air would be the obvious twin and is deliberately absent — that event is
     * client-only, so it would need a packet of its own to reach the store, for a second way to do
     * something there is already a way to do.
     */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!holdsMarker(event.getEntity(), event.getItemStack())) return;
        // Sneaking is the marking gesture and never the discarding one. Sharing it made the
        // outcome depend on whether the click happened to find a block — set corner two, or throw
        // the whole selection away.
        if (event.getEntity().isShiftKeyDown()) return;

        ServerPlayer player = (ServerPlayer) event.getEntity();
        // Nothing marked: leave the click alone rather than swallowing it and reporting a
        // discard that discarded nothing.
        if (ZoneSelection.of(player.getUUID()) == null) return;

        ZoneSelection.clear(player.getUUID());
        PacketHandler.sendZoneSelection(SyncZoneSelectionPacket.empty(), player);
        player.displayClientMessage(
                Component.translatable("command.historystages.zone.cleared"), true);

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        player.containerMenu.broadcastFullState();
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // A selection is a step that lasts minutes, not world state.
        ZoneSelection.clear(event.getEntity().getUUID());
    }

    /** Sneaking with the configured item in hand: the gesture that sets a corner. */
    private static boolean isMarking(net.minecraft.world.entity.player.Player player, ItemStack held) {
        return holdsMarker(player, held) && player.isShiftKeyDown();
    }

    /**
     * The configured item, in the hand of somebody allowed to use it, on the server.
     *
     * <p>Sneaking is asked separately, because the two gestures split on it: sneaking marks,
     * plain clicking discards.
     */
    private static boolean holdsMarker(net.minecraft.world.entity.player.Player player, ItemStack held) {
        // PlayerInteractEvent fires on both sides; running this on the client would write into a
        // store only the server keeps.
        if (player == null || player.level().isClientSide()) return false;
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        if (!serverPlayer.hasPermissions(2)) return false;

        Item marker = markerItem();
        return marker != null && held.is(marker);
    }

    private static void mark(ServerPlayer player, int corner, BlockPos pos) {
        String dimension = player.level().dimension().location().toString();
        ZoneSelection.setPoint(player.getUUID(), corner, pos.getX(), pos.getY(), pos.getZ(), dimension);
        PacketHandler.sendZoneSelection(
                SyncZoneSelectionPacket.of(ZoneSelection.of(player.getUUID())), player);

        player.displayClientMessage(Component.translatable("command.historystages.zone.marked",
                corner, pos.getX(), pos.getY(), pos.getZ()), true);
    }

    /** The configured item, or null when the setting is empty or names something unknown. */
    private static Item markerItem() {
        String id = Config.GAMEPLAY.zoneMarkerItem.get();
        if (id == null || id.isBlank()) return null;
        if (id.equals(cachedId)) return cachedItem;

        ResourceLocation location = ResourceLocation.tryParse(id);
        Item item = location == null ? null : BuiltInRegistries.ITEM.getOptional(location).orElse(null);
        if (item == null) {
            LOGGER.warn("[ZoneLock] Marker item '{}' is not a known item — marking by item is off", id);
        }

        cachedId = id;
        cachedItem = item;
        return item;
    }
}
