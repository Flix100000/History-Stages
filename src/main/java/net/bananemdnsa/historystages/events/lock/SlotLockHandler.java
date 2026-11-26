package net.bananemdnsa.historystages.events.lock;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.util.lock.LockFeedback;
import net.bananemdnsa.historystages.util.lock.SlotLocks;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.bananemdnsa.historystages.platform.bus.SubscribeEvent;
import net.bananemdnsa.historystages.platform.bus.EventBusSubscriber;
import net.bananemdnsa.historystages.platform.event.entity.living.LivingSwapItemsEvent;

/** Server-side slot lock events that do not go through a menu. */
@EventBusSubscriber(modid = HistoryStages.MOD_ID)
public final class SlotLockHandler {

    private static final int OFFHAND = 40;

    private SlotLockHandler() {}

    /** Last block each player right-clicked, with the tick it happened in. */
    private static final java.util.Map<java.util.UUID, ClickedBlock> LAST_CLICK = new java.util.HashMap<>();

    private record ClickedBlock(net.minecraft.core.BlockPos pos, long gameTime) {}

    @SubscribeEvent
    public static void onRightClickBlock(net.bananemdnsa.historystages.platform.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        LAST_CLICK.put(event.getEntity().getUUID(),
                new ClickedBlock(event.getPos(), event.getLevel().getGameTime()));
    }

    /**
     * Pins the menu to the block it was opened from and tells the client. Menus whose slots hold
     * the block entity already know; mod machines often keep items in their own handler, so a
     * right-click on a block entity in the same tick is the next best evidence.
     */
    @SubscribeEvent
    public static void onContainerOpen(net.bananemdnsa.historystages.platform.event.entity.player.PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var menu = event.getContainer();
        var lockMenu = (net.bananemdnsa.historystages.util.lock.SlotLockMenu) menu;
        String target = lockMenu.historystages$slotTarget();
        if (target == null) return;

        boolean fromSlots = menu.slots.stream()
                .anyMatch(s -> net.bananemdnsa.historystages.util.lock.SlotTargets.blockOf(s.container).isPresent());
        ClickedBlock click = LAST_CLICK.remove(player.getUUID());
        if (!fromSlots && click != null && click.gameTime() == player.level().getGameTime()
                && player.level().getBlockEntity(click.pos()) != null) {
            target = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(player.level().getBlockState(click.pos()).getBlock()).toString();
            lockMenu.historystages$setSlotTarget(target);
        }
        // Only worth a packet when something can be locked, or while the editor's in-world pick
        // needs to know which block was opened, which is also how the very first slot lock is made.
        boolean needed = net.bananemdnsa.historystages.data.lock.engine.StageLocks.engine().anySlotLocks()
                || net.bananemdnsa.historystages.network.serverbound.SlotPickModePacket.isPicking(player.getUUID());
        if (needed && player.connection != null) {
            ServerPlayNetworking.send(player,
                    new net.bananemdnsa.historystages.network.clientbound.SlotTargetPacket(menu.containerId, target));
        }
    }

    /** Every relock path posts one of these two, commands and team sharing included. */
    @SubscribeEvent
    public static void onGlobalLocked(net.bananemdnsa.historystages.api.stage.StageEvent.Locked event) {
        net.bananemdnsa.historystages.util.lock.SlotLockSweeper.sweepAll(
                net.bananemdnsa.historystages.util.ServerHolder.get());
    }

    @SubscribeEvent
    public static void onIndividualLocked(net.bananemdnsa.historystages.api.stage.StageEvent.IndividualLocked event) {
        var server = net.bananemdnsa.historystages.util.ServerHolder.get();
        if (server == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(event.getPlayerUUID());
        if (player != null) net.bananemdnsa.historystages.util.lock.SlotLockSweeper.sweep(player);
    }

    @SubscribeEvent
    public static void onLogout(net.bananemdnsa.historystages.platform.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_CLICK.remove(event.getEntity().getUUID());
        net.bananemdnsa.historystages.network.serverbound.SlotPickModePacket.forget(event.getEntity().getUUID());
    }

    /** Right-clicking armor equips it straight into its slot, past every menu check. */
    @SubscribeEvent
    public static void onRightClickItem(net.bananemdnsa.historystages.platform.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        var stack = event.getItemStack();
        if (stack.isEmpty() || !SlotLocks.anyPlayerSlotLocks(player)) return;
        var equipSlot = player.getEquipmentSlotForItem(stack);
        if (equipSlot.getType() != net.minecraft.world.entity.EquipmentSlot.Type.HUMANOID_ARMOR) return;
        // Inventory armor indices run feet (36) to head (39), the same order as the slot index.
        if (!SlotLocks.playerSlot(player, 36 + equipSlot.getIndex()).blocks()) return;
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
        if (player instanceof ServerPlayer serverPlayer) {
            LockFeedback.sendActionbar(serverPlayer, "slot",
                    Component.translatable("message.historystages.slot_locked"));
        }
    }

    /** F swaps main hand and offhand without any menu, so the slot checks never see it. */
    @SubscribeEvent
    public static void onSwapHands(LivingSwapItemsEvent.Hands event) {
        if (!(event.getEntity() instanceof Player player)) return;
        boolean blocked = SlotLocks.playerSlot(player, OFFHAND).blocks()
                || SlotLocks.playerSlot(player, player.getInventory().selected).blocks();
        if (!blocked) return;
        event.setCanceled(true);
        if (player instanceof ServerPlayer serverPlayer) {
            LockFeedback.sendActionbar(serverPlayer, "slot",
                    Component.translatable("message.historystages.slot_locked"));
        }
    }
}
