package net.bananemdnsa.historystages.fabric.mixin.event;

import java.util.OptionalInt;

import net.bananemdnsa.historystages.fabric.events.Phases;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.player.PlayerContainerEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.nautilus.AbstractNautilus;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises the container-open event NeoForge fires once a menu is open on the server: after
 * {@code openMenu} and after the mount inventories, which open without going through it.
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

    @Inject(method = "openNautilusInventory", at = @At("RETURN"))
    private void historystages$openedNautilus(AbstractNautilus nautilus, Container container, CallbackInfo ci) {
        historystages$raise();
    }

    private void historystages$raise() {
        Player self = (Player) (Object) this;
        if (self.containerMenu != self.inventoryMenu && Phases.wanted(PlayerContainerEvent.Open.class)) {
            EventBus.post(new PlayerContainerEvent.Open(self, self.containerMenu));
        }
    }
}
