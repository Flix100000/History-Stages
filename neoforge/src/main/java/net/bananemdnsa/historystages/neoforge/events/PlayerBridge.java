package net.bananemdnsa.historystages.neoforge.events;

import java.util.function.Consumer;

import net.bananemdnsa.historystages.platform.TriState;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.bus.EventPriority;
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

    private static void rightClickBlock(PlayerInteractEvent.RightClickBlock event, EventPriority priority) {
        if (!Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.player.PlayerInteractEvent.RightClickBlock.class, priority)) {
            return;
        }
        var platform = new net.bananemdnsa.historystages.platform.event.entity.player.PlayerInteractEvent.RightClickBlock(
                event.getEntity(), event.getHand(), event.getPos(), event.getFace());
        TriState seededBlock = Bridges.toPlatform(event.getUseBlock());
        TriState seededItem = Bridges.toPlatform(event.getUseItem());
        platform.setUseBlock(seededBlock);
        platform.setUseItem(seededItem);
        platform.setCancellationResult(event.getCancellationResult());
        Bridges.carryCancel(event, platform);
        EventBus.post(platform, priority);
        event.setCancellationResult(platform.getCancellationResult());
        writeUse(event.isCanceled(), platform.isCanceled(), seededBlock, seededItem,
                platform.getUseBlock(), platform.getUseItem(), event::setUseBlock, event::setUseItem,
                () -> Bridges.writeCancel(event, platform));
    }

    /**
     * NeoForge's {@code setCanceled(true)} forces useBlock and useItem to FALSE, and
     * {@code setCanceled(false)} leaves them there. So a cancel is written after the TriStates and an
     * un-cancel before them; on an un-cancel, a TriState the handler left at the FALSE the cancel had
     * put there goes back to DEFAULT, otherwise the interaction would stay blocked.
     */
    private static void writeUse(boolean nativeCanceled, boolean platformCanceled, TriState seededBlock,
                                 TriState seededItem, TriState useBlock, TriState useItem,
                                 Consumer<net.minecraft.util.TriState> setUseBlock,
                                 Consumer<net.minecraft.util.TriState> setUseItem, Runnable writeCancel) {
        boolean uncancel = nativeCanceled && !platformCanceled;
        if (uncancel) {
            writeCancel.run();
            if (useBlock == seededBlock && useBlock.isFalse()) {
                useBlock = TriState.DEFAULT;
            }
            if (useItem == seededItem && useItem.isFalse()) {
                useItem = TriState.DEFAULT;
            }
        }
        setUseBlock.accept(Bridges.toNative(useBlock));
        setUseItem.accept(Bridges.toNative(useItem));
        if (!uncancel) {
            writeCancel.run();
        }
    }

    private static void leftClickBlock(PlayerInteractEvent.LeftClickBlock event, EventPriority priority) {
        if (!Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.player.PlayerInteractEvent.LeftClickBlock.class, priority)) {
            return;
        }
        var platform = new net.bananemdnsa.historystages.platform.event.entity.player.PlayerInteractEvent.LeftClickBlock(
                event.getEntity(), event.getPos(), event.getFace());
        TriState seededBlock = Bridges.toPlatform(event.getUseBlock());
        TriState seededItem = Bridges.toPlatform(event.getUseItem());
        platform.setUseBlock(seededBlock);
        platform.setUseItem(seededItem);
        Bridges.carryCancel(event, platform);
        EventBus.post(platform, priority);
        writeUse(event.isCanceled(), platform.isCanceled(), seededBlock, seededItem,
                platform.getUseBlock(), platform.getUseItem(), event::setUseBlock, event::setUseItem,
                () -> Bridges.writeCancel(event, platform));
    }

    private static void rightClickItem(PlayerInteractEvent.RightClickItem event, EventPriority priority) {
        if (!Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.player.PlayerInteractEvent.RightClickItem.class, priority)) {
            return;
        }
        var platform = new net.bananemdnsa.historystages.platform.event.entity.player.PlayerInteractEvent.RightClickItem(
                event.getEntity(), event.getHand());
        platform.setCancellationResult(event.getCancellationResult());
        Bridges.post(event, platform, priority);
        event.setCancellationResult(platform.getCancellationResult());
    }

    /**
     * NeoForge 26.2 folded {@code EntityInteractSpecific} into {@code EntityInteract}, which now
     * carries the local hit position. Main-branch handlers listen for both, so both are posted:
     * the specific one first, as vanilla used to ask {@code interactAt} before {@code interact}.
     * When a handler cancels the specific one, the plain one is not asked any more; the interaction
     * is over. When the native event already arrived cancelled, both are still posted, so handlers
     * with {@code receiveCanceled} see both.
     *
     * <p>Vanilla 26.2 sends one interact packet per click and NeoForge fires one EntityInteract from
     * it (a GameTest checks this), so each platform event is posted once per interaction.
     */
    private static void entityInteract(PlayerInteractEvent.EntityInteract event, EventPriority priority) {
        boolean arrivedCanceled = event.isCanceled();
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.player.PlayerInteractEvent.EntityInteractSpecific.class, priority)) {
            var specific = new net.bananemdnsa.historystages.platform.event.entity.player.PlayerInteractEvent.EntityInteractSpecific(
                    event.getEntity(), event.getHand(), event.getTarget(), event.getLocation());
            specific.setCancellationResult(event.getCancellationResult());
            Bridges.post(event, specific, priority);
            event.setCancellationResult(specific.getCancellationResult());
            if (specific.isCanceled() && !arrivedCanceled) {
                return;
            }
        }
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.player.PlayerInteractEvent.EntityInteract.class, priority)) {
            var plain = new net.bananemdnsa.historystages.platform.event.entity.player.PlayerInteractEvent.EntityInteract(
                    event.getEntity(), event.getHand(), event.getTarget());
            plain.setCancellationResult(event.getCancellationResult());
            Bridges.post(event, plain, priority);
            event.setCancellationResult(plain.getCancellationResult());
        }
    }

    private static void attackEntity(AttackEntityEvent event, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.player.AttackEntityEvent.class, priority)) {
            Bridges.post(event, new net.bananemdnsa.historystages.platform.event.entity.player.AttackEntityEvent(
                    event.getEntity(), event.getTarget()), priority);
        }
    }

    private static void anvilUpdate(AnvilUpdateEvent event, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.AnvilUpdateEvent.class, priority)) {
            Bridges.post(event, new net.bananemdnsa.historystages.platform.event.AnvilUpdateEvent(
                    event.getLeft(), event.getRight(), event.getPlayer()), priority);
        }
    }

    private static void pickupPre(ItemEntityPickupEvent.Pre event, EventPriority priority) {
        if (!Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.player.ItemEntityPickupEvent.Pre.class, priority)) {
            return;
        }
        var platform = new net.bananemdnsa.historystages.platform.event.entity.player.ItemEntityPickupEvent.Pre(
                event.getPlayer(), event.getItemEntity());
        platform.setCanPickup(Bridges.toPlatform(event.canPickup()));
        EventBus.post(platform, priority);
        event.setCanPickup(Bridges.toNative(platform.canPickup()));
    }

    private static void pickupPost(ItemEntityPickupEvent.Post event, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.player.ItemEntityPickupEvent.Post.class, priority)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.entity.player.ItemEntityPickupEvent.Post(
                    event.getPlayer(), event.getItemEntity()), priority);
        }
    }

    /** The tooltip list is handed over as is, so lines added or removed land in the real tooltip. */
    private static void tooltip(ItemTooltipEvent event, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.player.ItemTooltipEvent.class, priority)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.entity.player.ItemTooltipEvent(
                    event.getItemStack(), event.getToolTip()), priority);
        }
    }

    private static void breakSpeed(PlayerEvent.BreakSpeed event, EventPriority priority) {
        if (!Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.player.PlayerEvent.BreakSpeed.class, priority)) {
            return;
        }
        var platform = new net.bananemdnsa.historystages.platform.event.entity.player.PlayerEvent.BreakSpeed(
                event.getEntity(), event.getState(), event.getOriginalSpeed());
        // Another mod may already have changed the speed; the handlers build on that, not on vanilla's.
        platform.setNewSpeed(event.getNewSpeed());
        EventBus.post(platform, priority);
        event.setNewSpeed(platform.getNewSpeed());
    }

    private static void loggedIn(PlayerEvent.PlayerLoggedInEvent event, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.player.PlayerEvent.PlayerLoggedInEvent.class, priority)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.entity.player.PlayerEvent.PlayerLoggedInEvent(
                    event.getEntity()), priority);
        }
    }

    private static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.player.PlayerEvent.PlayerLoggedOutEvent.class, priority)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(
                    event.getEntity()), priority);
        }
    }

    private static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent.class, priority)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent(
                    event.getEntity(), event.getFrom(), event.getTo()), priority);
        }
    }

    private static void tickPost(PlayerTickEvent.Post event, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.tick.PlayerTickEvent.Post.class, priority)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.tick.PlayerTickEvent.Post(event.getEntity()), priority);
        }
    }

    private static void advancementEarned(AdvancementEvent.AdvancementEarnEvent event, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.player.AdvancementEvent.AdvancementEarnEvent.class, priority)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.entity.player.AdvancementEvent.AdvancementEarnEvent(
                    event.getEntity(), event.getAdvancement()), priority);
        }
    }
}
