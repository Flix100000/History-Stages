package net.bananemdnsa.historystages.mixin.event;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.living.LivingSwapItemsEvent;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Raises the hand-swap event where NeoForge does, before the server trades main hand and offhand.
 *
 * <p>After the thread check, not at the head: the packet arrives on the network thread first and
 * vanilla sends it round again to the server thread.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SwapHandsMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handlePlayerAction", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
            shift = At.Shift.AFTER))
    private void historystages$raise(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (packet.getAction() != ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND) return;
        if (player.isSpectator()) return;
        if (EventBus.post(new LivingSwapItemsEvent.Hands(player)).isCanceled()) ci.cancel();
    }
}
