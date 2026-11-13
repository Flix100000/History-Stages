package net.bananemdnsa.historystages.fabric.mixin.event;

import net.bananemdnsa.historystages.fabric.events.Phases;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.living.LivingGetProjectileEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises the projectile event with the ammo vanilla picked and hands back what the handlers chose.
 * At RETURN so vanilla's search order stays. Both classes, as NeoForge hooks both: the player
 * override does not call the living-entity version.
 */
@Mixin({LivingEntity.class, Player.class})
public abstract class GetProjectileMixin {

    @Inject(method = "getProjectile", at = @At("RETURN"), cancellable = true)
    private void historystages$raise(ItemStack weapon, CallbackInfoReturnable<ItemStack> cir) {
        if (!Phases.wanted(LivingGetProjectileEvent.class)) {
            return;
        }
        ItemStack picked = cir.getReturnValue();
        LivingGetProjectileEvent event = EventBus.post(
                new LivingGetProjectileEvent((LivingEntity) (Object) this, weapon, picked));
        if (event.getProjectileItemStack() != picked) {
            cir.setReturnValue(event.getProjectileItemStack());
        }
    }
}
