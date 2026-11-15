package net.bananemdnsa.historystages.mixin.event;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.player.AdvancementEvent;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises the advancement-earned event once, when the advancement completes. It is one of the
 * things an auto-trigger can wait for.
 *
 * <p>Not at RETURN: {@code award} answers true for every criterion it grants, so an advancement
 * with two criteria unlocked its stage after the first. The rewards are handed out only in the
 * branch where the advancement just became done, which is the moment NeoForge raises its event.
 * Like NeoForge, only for advancements with a display; recipe unlocks and other hidden
 * bookkeeping are not "earned".
 */
@Mixin(PlayerAdvancements.class)
public abstract class AdvancementEarnMixin {

    @Shadow
    private ServerPlayer player;

    @Inject(method = "award", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/advancements/AdvancementRewards;grant(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private void historystages$raise(AdvancementHolder advancement, String criterion,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (player != null && advancement.value().display().isPresent()) {
            EventBus.post(new AdvancementEvent.AdvancementEarnEvent(player, advancement));
        }
    }
}
