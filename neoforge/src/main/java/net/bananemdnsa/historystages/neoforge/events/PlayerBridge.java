package net.bananemdnsa.historystages.neoforge.events;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Player actions: clicks, attacks, pickups, logins, ticks. */
public final class PlayerBridge {

    private PlayerBridge() {}

    public static void register(IEventBus bus) {
        Bridges.listenCancellable(bus, PlayerInteractEvent.RightClickBlock.class, PlayerBridge::rightClickBlock);
        Bridges.listenCancellable(bus, PlayerInteractEvent.LeftClickBlock.class, PlayerBridge::leftClickBlock);
        Bridges.listenCancellable(bus, PlayerInteractEvent.RightClickItem.class, PlayerBridge::rightClickItem);
        Bridges.listenCancellable(bus, PlayerInteractEvent.EntityInteract.class, PlayerBridge::entityInteract);
        Bridges.listenCancellable(bus, AttackEntityEvent.class, PlayerBridge::attackEntity);
        Bridges.listenCancellable(bus, AnvilUpdateEvent.class, PlayerBridge::anvilUpdate);
        Bridges.listen(bus, ItemEntityPickupEvent.Pre.class, PlayerBridge::pickupPre);
        Bridges.listen(bus, ItemEntityPickupEvent.Post.class, PlayerBridge::pickupPost);
        Bridges.listen(bus, ItemTooltipEvent.class, PlayerBridge::tooltip);
        Bridges.listen(bus, PlayerEvent.BreakSpeed.class, PlayerBridge::breakSpeed);
        Bridges.listen(bus, PlayerEvent.PlayerLoggedInEvent.class, PlayerBridge::loggedIn);
        Bridges.listen(bus, PlayerEvent.PlayerLoggedOutEvent.class, PlayerBridge::loggedOut);
        Bridges.listen(bus, PlayerEvent.PlayerChangedDimensionEvent.class, PlayerBridge::changedDimension);
        Bridges.listen(bus, PlayerTickEvent.Post.class, PlayerBridge::tickPost);
        Bridges.listen(bus, AdvancementEvent.AdvancementEarnEvent.class, PlayerBridge::advancementEarned);
    }

    private static void rightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.player.PlayerInteractEvent.RightClickBlock.class)) {
            return;
        }
        var platform = new net.bananemdnsa.historystages.platform.event.entity.player.PlayerInteractEvent.RightClickBlock(
                event.getEntity(), event.getHand(), event.getPos(), event.getFace());
        platform.setUseBlock(Bridges.toPlatform(event.getUseBlock()));
        platform.setUseItem(Bridges.toPlatform(event.getUseItem()));
        platform.setCancellationResult(event.getCancellationResult());
        Bridges.carryCancel(event, platform);
        EventBus.post(platform);
        event.setUseBlock(Bridges.toNative(platform.getUseBlock()));
        event.setUseItem(Bridges.toNative(platform.getUseItem()));
        event.setCancellationResult(platform.getCancellationResult());
        // Last: NeoForge's setCanceled(true) also forces useBlock and useItem to FALSE.
        Bridges.writeCancel(event, platform);
    }

    private static void leftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.player.PlayerInteractEvent.LeftClickBlock.class)) {
            return;
        }
        var platform = new net.bananemdnsa.historystages.platform.event.entity.player.PlayerInteractEvent.LeftClickBlock(
                event.getEntity(), event.getPos(), event.getFace());
        platform.setUseBlock(Bridges.toPlatform(event.getUseBlock()));
        platform.setUseItem(Bridges.toPlatform(event.getUseItem()));
        Bridges.carryCancel(event, platform);
        EventBus.post(platform);
        event.setUseBlock(Bridges.toNative(platform.getUseBlock()));
        event.setUseItem(Bridges.toNative(platform.getUseItem()));
        Bridges.writeCancel(event, platform);
    }

    private static void rightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.player.PlayerInteractEvent.RightClickItem.class)) {
            return;
        }
        var platform = new net.bananemdnsa.historystages.platform.event.entity.player.PlayerInteractEvent.RightClickItem(
                event.getEntity(), event.getHand());
        platform.setCancellationResult(event.getCancellationResult());
        Bridges.post(event, platform);
        event.setCancellationResult(platform.getCancellationResult());
    }

    /**
     * NeoForge 26.2 folded {@code EntityInteractSpecific} into {@code EntityInteract}, which now
     * carries the local hit position. Main-branch handlers listen for both, so both are posted:
     * the specific one first, as vanilla used to ask {@code interactAt} before {@code interact}.
     * Once it is cancelled the plain one is not asked any more; the interaction is over.
     */
    private static void entityInteract(PlayerInteractEvent.EntityInteract event) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.player.PlayerInteractEvent.EntityInteractSpecific.class)) {
            var specific = new net.bananemdnsa.historystages.platform.event.entity.player.PlayerInteractEvent.EntityInteractSpecific(
                    event.getEntity(), event.getHand(), event.getTarget(), event.getLocation());
            specific.setCancellationResult(event.getCancellationResult());
            Bridges.post(event, specific);
            event.setCancellationResult(specific.getCancellationResult());
            if (specific.isCanceled()) {
                return;
            }
        }
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.player.PlayerInteractEvent.EntityInteract.class)) {
            var plain = new net.bananemdnsa.historystages.platform.event.entity.player.PlayerInteractEvent.EntityInteract(
                    event.getEntity(), event.getHand(), event.getTarget());
            plain.setCancellationResult(event.getCancellationResult());
            Bridges.post(event, plain);
            event.setCancellationResult(plain.getCancellationResult());
        }
    }

    private static void attackEntity(AttackEntityEvent event) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.player.AttackEntityEvent.class)) {
            Bridges.post(event, new net.bananemdnsa.historystages.platform.event.entity.player.AttackEntityEvent(
                    event.getEntity(), event.getTarget()));
        }
    }

    private static void anvilUpdate(AnvilUpdateEvent event) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.AnvilUpdateEvent.class)) {
            Bridges.post(event, new net.bananemdnsa.historystages.platform.event.AnvilUpdateEvent(
                    event.getLeft(), event.getRight(), event.getPlayer()));
        }
    }

    private static void pickupPre(ItemEntityPickupEvent.Pre event) {
        if (!Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.player.ItemEntityPickupEvent.Pre.class)) {
            return;
        }
        var platform = new net.bananemdnsa.historystages.platform.event.entity.player.ItemEntityPickupEvent.Pre(
                event.getPlayer(), event.getItemEntity());
        platform.setCanPickup(Bridges.toPlatform(event.canPickup()));
        EventBus.post(platform);
        event.setCanPickup(Bridges.toNative(platform.canPickup()));
    }

    private static void pickupPost(ItemEntityPickupEvent.Post event) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.player.ItemEntityPickupEvent.Post.class)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.entity.player.ItemEntityPickupEvent.Post(
                    event.getPlayer(), event.getItemEntity()));
        }
    }

    /** The tooltip list is handed over as is, so lines added or removed land in the real tooltip. */
    private static void tooltip(ItemTooltipEvent event) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.player.ItemTooltipEvent.class)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.entity.player.ItemTooltipEvent(
                    event.getItemStack(), event.getToolTip()));
        }
    }

    private static void breakSpeed(PlayerEvent.BreakSpeed event) {
        if (!Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.player.PlayerEvent.BreakSpeed.class)) {
            return;
        }
        var platform = new net.bananemdnsa.historystages.platform.event.entity.player.PlayerEvent.BreakSpeed(
                event.getEntity(), event.getState(), event.getOriginalSpeed());
        // Another mod may already have changed the speed; the handlers build on that, not on vanilla's.
        platform.setNewSpeed(event.getNewSpeed());
        EventBus.post(platform);
        event.setNewSpeed(platform.getNewSpeed());
    }

    private static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.player.PlayerEvent.PlayerLoggedInEvent.class)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.entity.player.PlayerEvent.PlayerLoggedInEvent(
                    event.getEntity()));
        }
    }

    private static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.player.PlayerEvent.PlayerLoggedOutEvent.class)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(
                    event.getEntity()));
        }
    }

    private static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent.class)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent(
                    event.getEntity(), event.getFrom(), event.getTo()));
        }
    }

    private static void tickPost(PlayerTickEvent.Post event) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.tick.PlayerTickEvent.Post.class)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.tick.PlayerTickEvent.Post(event.getEntity()));
        }
    }

    private static void advancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.player.AdvancementEvent.AdvancementEarnEvent.class)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.entity.player.AdvancementEvent.AdvancementEarnEvent(
                    event.getEntity(), event.getAdvancement()));
        }
    }
}
