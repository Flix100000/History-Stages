package net.bananemdnsa.historystages.mixin.event;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.living.BabyEntitySpawnEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Raises the breeding event.
 *
 * <p>Breeding never reaches the ordinary spawn path: vanilla builds the baby and adds it to the
 * world directly, so a spawn lock would miss it entirely without this. Cancelling means no baby,
 * while the parents still lose their food and their love — the same bargain the other loader
 * strikes here.
 */
@Mixin(Animal.class)
public abstract class BabyEntitySpawnMixin {

    @Inject(method = "finalizeSpawnChildFromBreeding", at = @At("HEAD"), cancellable = true)
    private void historystages$raise(ServerLevel level, Animal partner, AgeableMob child,
                                     CallbackInfo ci) {
        BabyEntitySpawnEvent event = EventBus.post(
                new BabyEntitySpawnEvent((Animal) (Object) this, partner, child));
        if (event.isCanceled()) {
            ci.cancel();
        }
    }
}
