package net.bananemdnsa.historystages.fabric.mixin.event;

import net.bananemdnsa.historystages.fabric.events.Phases;
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
 * Raises the advancement-earned event. {@code award} runs per criterion and answers true once,
 * when the last criterion lands and the advancement is complete.
 */
@Mixin(PlayerAdvancements.class)
public abstract class AdvancementEarnMixin {

    @Shadow
    private ServerPlayer player;

    @Inject(method = "award", at = @At("RETURN"))
    private void historystages$raise(AdvancementHolder advancement, String criterion,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue()) && player != null
                && Phases.wanted(AdvancementEvent.AdvancementEarnEvent.class)) {
            EventBus.post(new AdvancementEvent.AdvancementEarnEvent(player, advancement));
        }
    }
}
