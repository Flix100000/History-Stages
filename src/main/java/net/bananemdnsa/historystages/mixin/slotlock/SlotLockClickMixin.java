package net.bananemdnsa.historystages.mixin.slotlock;

import net.bananemdnsa.historystages.util.lock.LockFeedback;
import net.bananemdnsa.historystages.util.lock.SlotLocks;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Catches what the slot checks miss: mod slots that override mayPlace/mayPickup without calling
 * super, and the number-key swap, which writes the hotbar slot straight through the inventory.
 * Runs on both sides so the client does not predict a move the server will refuse.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class SlotLockClickMixin {

    /** Offhand as the swap button means "F", which vanilla sends as button 40. */
    private static final int OFFHAND_BUTTON = 40;

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void historystages$clicked(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        // The editor's in-world pick turns container clicks into slot selection; nothing may move.
        if (player instanceof ServerPlayer picker
                && net.bananemdnsa.historystages.network.serverbound.SlotPickModePacket.isPicking(picker.getUUID())) {
            ci.cancel();
            return;
        }
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (slotId < 0 || slotId >= menu.slots.size()) return;
        // Drags are checked slot by slot through mayPlace; the drag start carries no real slot.
        if (clickType == ClickType.QUICK_CRAFT) return;

        Slot slot = menu.slots.get(slotId);
        boolean blocked = SlotLocks.stateOf(slot, player).blocks();
        if (!blocked && clickType == ClickType.SWAP
                && (button == OFFHAND_BUTTON || (button >= 0 && button < 9))) {
            blocked = SlotLocks.playerSlot(player, button).blocks();
        }
        if (!blocked) return;

        ci.cancel();
        if (player instanceof ServerPlayer serverPlayer) {
            LockFeedback.sendActionbar(serverPlayer, "slot",
                    Component.translatable("message.historystages.slot_locked"));
        }
    }
}
