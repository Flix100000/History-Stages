package net.bananemdnsa.historystages.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.TradeLockedPacket;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.lock.TradeLockHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The first trade seam: the player is shown only the offers they may see.
 *
 * <p>On the interface rather than on {@code Villager}, and that is the whole point. Neither
 * {@code AbstractVillager}, {@code Villager} nor {@code WanderingTrader} overrides
 * {@link Merchant#openTradingScreen} — all three inherit it — and so does every merchant any
 * other mod writes. One seam therefore covers a modded trader nobody here has ever heard of,
 * without naming its class. A merchant that <em>does</em> override it is past us; the second seam
 * below still refuses the purchase.
 *
 * <p><strong>Only the one call that hands the list over is wrapped.</strong> Not the method around
 * it. Cancelling {@code openTradingScreen} and running our own version of its body — which is what
 * this did until #130's round of cleanup — takes the method away from everyone else who injected
 * into it, every time a stage happens to gate an offer, and leaves a copy of one Minecraft
 * version's code sitting in the mod for the next port to get wrong.
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
@Mixin(Merchant.class)
public interface MerchantOffersMixin {

    @WrapOperation(
            method = "openTradingScreen",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;sendMerchantOffers"
                            + "(ILnet/minecraft/world/item/trading/MerchantOffers;IIZZ)V"))
    private void historystages$sendOnlyWhatIsUnlocked(Player player, int containerId,
                                                      MerchantOffers offers, int level, int xp,
                                                      boolean showProgress, boolean canRestock,
                                                      Operation<Void> original) {
        Merchant merchant = (Merchant) this;
        if (merchant.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            original.call(player, containerId, offers, level, xp, showProgress, canRestock);
            return;
        }

        TradeLockHelper.Filtered filtered =
                TradeLockHelper.filterForPlayer(offers, merchant, level, serverPlayer);
        if (!filtered.removedAnything()) {
            original.call(player, containerId, offers, level, xp, showProgress, canRestock);
            return;
        }

        MerchantOffers shown = new MerchantOffers();
        for (int index : filtered.keptIndices()) {
            shown.add(offers.get(index));
        }
        original.call(player, containerId, shown, level, xp, showProgress, canRestock);

        DebugLogger.runtimeThrottled("Trade Lock",
                "trade_" + serverPlayer.getUUID() + "_" + filtered.gatingStages(),
                "<" + serverPlayer.getName().getString() + "> " + filtered.keptIndices().size()
                        + " of " + filtered.offeredCount() + " offers shown — held back by: "
                        + filtered.gatingStages());

        // Only when nothing at all survived. An empty list is sent rather than no list: the client
        // then knows it has none, instead of sitting on the menu's own empty one waiting.
        if (filtered.removedEverything()) {
            PacketHandler.sendTradeLockedToPlayer(
                    new TradeLockedPacket(containerId,
                            TradeLockHelper.displayNamesOf(filtered.gatingStages()),
                            TradeLockHelper.kindOf(filtered.gatingStages())),
                    serverPlayer);
        }
    }
}
