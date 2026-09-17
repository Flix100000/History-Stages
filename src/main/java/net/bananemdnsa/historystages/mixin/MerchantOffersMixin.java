package net.bananemdnsa.historystages.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.TradeLockedPacket;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.lock.TradeLockHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;

/**
 * The first trade seam: the player is shown only the offers they may see.
 *
 * <p>On the send rather than on {@code Merchant.openTradingScreen}, and that is the whole point.
 * Every merchant — villager, wandering trader, or one another mod wrote — ends up here, because
 * this is the one place an offer list leaves the server for a player. It also happens to be the
 * only option: Mixin cannot inject into an interface, which is where {@code openTradingScreen}
 * lives.
 *
 * <p><strong>The method is wrapped, not taken over.</strong> Cancelling it and sending the packet
 * ourselves would take it away from everyone else who injected into it, every time a stage
 * happens to gate an offer — the same shape as the Polymorph report (#127) — and would leave a
 * copy of one Minecraft version's code sitting in the mod for the next port to get wrong.
 *
 * <p><strong>The merchant keeps its real list.</strong> Only the copy sent to this player is
 * short. {@code overrideOffers} would change the merchant permanently, and a stage that is later
 * unlocked could not give the offer back — the one-way behaviour this whole category was designed
 * to avoid.
 *
 * <p>Nothing is drawn as locked, because there is no locked trade to draw: it is simply absent.
 * A merchant with one offer instead of two is unremarkable. A merchant with <em>none</em> is not,
 * so that case — and only that case — says why, and it says it inside the window the player is
 * looking at rather than in the actionbar under it. The player is staring at an empty list; the
 * explanation belongs where they are looking.
 *
 * <p>This seam is about what the player sees. It is not the security boundary: a modified client
 * could ask to pay for an offer it was never sent, which is what {@link MerchantContainerMixin}
 * is for.
 */
@Mixin(ServerPlayer.class)
public abstract class MerchantOffersMixin {

    @WrapMethod(method = "sendMerchantOffers")
    private void historystages$sendOnlyWhatIsUnlocked(int containerId, MerchantOffers offers,
                                                      int level, int xp, boolean showProgress,
                                                      boolean canRestock, Operation<Void> original) {
        Merchant merchant = historystages$tradingWith();
        if (merchant == null || offers.isEmpty()) {
            original.call(containerId, offers, level, xp, showProgress, canRestock);
            return;
        }

        ServerPlayer player = (ServerPlayer) (Object) this;
        TradeLockHelper.Filtered filtered =
                TradeLockHelper.filterForPlayer(offers, merchant, level, player);
        if (!filtered.removedAnything()) {
            original.call(containerId, offers, level, xp, showProgress, canRestock);
            return;
        }

        MerchantOffers shown = new MerchantOffers();
        for (int index : filtered.keptIndices()) {
            shown.add(offers.get(index));
        }
        original.call(containerId, shown, level, xp, showProgress, canRestock);

        DebugLogger.runtimeThrottled("Trade Lock",
                "trade_" + player.getUUID() + "_" + filtered.gatingStages(),
                "<" + player.getName().getString() + "> " + filtered.keptIndices().size()
                        + " of " + filtered.offeredCount() + " offers shown — held back by: "
                        + filtered.gatingStages());

        // Only when nothing at all survived. An empty list is sent rather than no list: the client
        // then knows it has none, instead of sitting on the menu's own empty one waiting.
        if (filtered.removedEverything()) {
            PacketHandler.sendTradeLockedToPlayer(
                    new TradeLockedPacket(containerId,
                            TradeLockHelper.displayNamesOf(filtered.gatingStages()),
                            TradeLockHelper.kindOf(filtered.gatingStages())),
                    player);
        }
    }

    /**
     * Who this player is trading with, read off the menu that was opened a moment earlier.
     *
     * <p>Vanilla opens the menu before it sends the offers, so it is already in place. Anything
     * that sends an offer list without one is left alone rather than guessed at — the payment
     * seam still refuses what it must.
     */
    private Merchant historystages$tradingWith() {
        ServerPlayer player = (ServerPlayer) (Object) this;
        return player.containerMenu instanceof MerchantMenu menu
                ? ((MerchantMenuAccessor) menu).historystages$getTrader()
                : null;
    }
}
