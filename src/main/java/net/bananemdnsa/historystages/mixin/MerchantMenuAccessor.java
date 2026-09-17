package net.bananemdnsa.historystages.mixin;

import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read access to the merchant behind an open trading menu. {@link MerchantOffersMixin} needs to
 * know who is trading, and the menu is the only thing that still holds it by the time the offers
 * are sent.
 */
@Mixin(MerchantMenu.class)
public interface MerchantMenuAccessor {
    @Accessor("trader")
    Merchant historystages$getTrader();
}
