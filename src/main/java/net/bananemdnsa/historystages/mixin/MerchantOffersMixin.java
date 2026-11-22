package net.bananemdnsa.historystages.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.TradeLockedPacket;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.HsRuntimeCategories;
import net.bananemdnsa.historystages.util.lock.TradeIndexMap;
import net.bananemdnsa.historystages.util.lock.TradeLockHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;

/**
 * The first trade seam: the player is shown only the offers they may see.
 *
 * <p>On the one method every offer list passes through on its way to a player. Opening the
 * screen and {@code Villager.resendOffersToTradingPlayer} (restock, catching up on demand) both
 * end here, and so does any modded merchant, whether or not it overrides
 * {@code openTradingScreen}. Wrapping only the call inside {@code openTradingScreen}, as this did
 * before, let a restock send the full list into the open screen, locked offers included.
 *
 * <p>The rows the player was sent are remembered ({@link TradeIndexMap}) so a click selects the
 * offer that row stands for, not whatever sits at that index in the merchant's full list.
 *
 * <p>The merchant comes from the open menu: every caller sends the offers for the menu that is
 * open. Anything else goes through untouched.
 *
 * <p><strong>The merchant keeps its real list.</strong> Only the copy sent to this player is
 * short. {@code overrideOffers} would change the merchant permanently, and a stage that is later
 * unlocked could not give the offer back.
 *
 * <p>Nothing is drawn as locked: the offer is simply absent. A merchant with <em>none</em> left
 * says why, inside the trade window the player is looking at.
 *
 * <p>This seam is about what the player sees. It is not the security boundary: a modified client
 * could ask to pay for an offer it was never sent, which is what {@link MerchantContainerMixin}
 * is for.
 */
@Mixin(ServerPlayer.class)
public abstract class MerchantOffersMixin {

    @WrapMethod(method = "sendMerchantOffers")
    private void historystages$sendOnlyWhatIsUnlocked(int containerId, MerchantOffers offers, int level, int xp,
                                                      boolean showProgress, boolean canRestock,
                                                      Operation<Void> original) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (!(player.containerMenu instanceof MerchantMenu menu) || menu.containerId != containerId) {
            original.call(containerId, offers, level, xp, showProgress, canRestock);
            return;
        }
        Merchant merchant = ((MerchantMenuAccessor) menu).historystages$trader();
        TradeLockHelper.Filtered filtered = TradeLockHelper.filterForPlayer(offers, merchant, level, player);
        if (!filtered.removedAnything()) {
            TradeIndexMap.shown(player.getUUID(), containerId, null);
            original.call(containerId, offers, level, xp, showProgress, canRestock);
            return;
        }
        TradeIndexMap.shown(player.getUUID(), containerId, filtered.keptIndices());
        MerchantOffers shown = new MerchantOffers();
        for (int index : filtered.keptIndices()) {
            shown.add(offers.get(index));
        }
        original.call(containerId, shown, level, xp, showProgress, canRestock);

        DebugLogger.runtimeThrottled(HsRuntimeCategories.TRADE_LOCK,
                "trade_" + player.getUUID() + "_" + filtered.gatingStages(),
                "<" + player.getName().getString() + "> " + filtered.keptIndices().size()
                        + " of " + filtered.offeredCount() + " offers shown — held back by: "
                        + filtered.gatingStages());

        // Only when nothing at all survived. An empty list is sent rather than no list: the client
        // then knows it has none, instead of sitting on the menu's own empty one waiting.
        if (filtered.removedEverything()) {
            PacketHandler.sendTradeLockedToPlayer(
                    new TradeLockedPacket(containerId,
                            TradeLockHelper.displayNamesOf(filtered.gatingStages(), player.getUUID()),
                            TradeLockHelper.kindOf(filtered.gatingStages())),
                    player);
        }
    }
}
