package net.bananemdnsa.historystages.mixin;

import net.bananemdnsa.historystages.util.lock.TradeIndexMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forgets the shown trade rows when the screen closes; disconnecting closes it too. */
@Mixin(MerchantMenu.class)
public class MerchantMenuCloseMixin {

    @Inject(method = "removed", at = @At("TAIL"))
    private void historystages$forgetRows(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer) {
            TradeIndexMap.forget(player.getUUID(), ((MerchantMenu) (Object) this).containerId);
        }
    }
}
