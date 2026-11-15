package net.bananemdnsa.historystages.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.bananemdnsa.historystages.util.lock.TradeIndexMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Translates the trade row a player clicked into the merchant's own index when locked offers
 * were held back from that player ({@link TradeIndexMap}).
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class SelectTradeMixin {

    @Shadow public ServerPlayer player;

    @ModifyExpressionValue(method = "handleSelectTrade", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ServerboundSelectTradePacket;getItem()I"))
    private int historystages$serverIndex(int row) {
        return TradeIndexMap.serverIndex(this.player.getUUID(), this.player.containerMenu.containerId, row);
    }
}
