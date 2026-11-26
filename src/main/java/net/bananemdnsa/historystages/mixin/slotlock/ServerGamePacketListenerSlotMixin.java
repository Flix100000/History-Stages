package net.bananemdnsa.historystages.mixin.slotlock;

import net.bananemdnsa.historystages.util.lock.SlotLocks;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The client never selects a locked hotbar slot on its own; this is for clients that try anyway. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerSlotMixin {

    @Shadow public ServerPlayer player;

    @Inject(method = "handleSetCarriedItem", at = @At("HEAD"), cancellable = true)
    private void historystages$handleSetCarriedItem(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        int slot = packet.getSlot();
        if (slot < 0 || slot >= 9) return;
        if (!player.server.isSameThread()) return; // vanilla re-schedules itself onto the server thread
        if (!SlotLocks.playerSlot(player, slot).blocks()) return;
        ci.cancel();
        player.connection.send(new ClientboundSetCarriedItemPacket(player.getInventory().selected));
    }
}
