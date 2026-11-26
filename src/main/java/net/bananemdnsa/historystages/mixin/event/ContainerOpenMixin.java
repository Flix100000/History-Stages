package net.bananemdnsa.historystages.mixin.event;

import java.util.OptionalInt;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.player.PlayerContainerEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises the container-open event at the two places NeoForge does: a menu opened through a
 * provider, and a horse's inventory. At the end of each, so the menu is already the player's.
 */
@Mixin(ServerPlayer.class)
public abstract class ContainerOpenMixin {

    @Inject(method = "openMenu", at = @At("RETURN"))
    private void historystages$opened(MenuProvider provider, CallbackInfoReturnable<OptionalInt> cir) {
        if (cir.getReturnValue().isPresent()) historystages$raise();
    }

    @Inject(method = "openHorseInventory", at = @At("RETURN"))
    private void historystages$openedHorse(AbstractHorse horse, Container container, CallbackInfo ci) {
        historystages$raise();
    }

    private void historystages$raise() {
        Player self = (Player) (Object) this;
        if (self.containerMenu != self.inventoryMenu) {
            EventBus.post(new PlayerContainerEvent.Open(self, self.containerMenu));
        }
    }
}
