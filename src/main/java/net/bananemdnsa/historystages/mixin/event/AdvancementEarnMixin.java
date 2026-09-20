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
 * Raises the advancement-earned event, which is one of the things an auto-trigger can wait for.
 *
 * <p>Only when the advancement is actually complete: award is called for every criterion, and
 * answers true just once, when the last one lands.
 */
@Mixin(PlayerAdvancements.class)
public abstract class AdvancementEarnMixin {

    @Shadow
    private ServerPlayer player;

    @Inject(method = "award", at = @At("RETURN"))
    private void historystages$raise(AdvancementHolder advancement, String criterion,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue()) && player != null) {
            EventBus.post(new AdvancementEvent.AdvancementEarnEvent(player, advancement));
        }
    }
}
