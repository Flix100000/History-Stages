package net.bananemdnsa.historystages.fabric.mixin.event;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bananemdnsa.historystages.fabric.events.Phases;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.living.BabyEntitySpawnEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Raises the breeding event right after vanilla picks the baby, where NeoForge raises it.
 *
 * <p>Answering null from the wrapped call keeps the baby out: vanilla skips the rest of
 * {@code spawnChildFromBreeding} for a null child. The fabric-1.21.X spot, cancelling
 * {@code finalizeSpawnChildFromBreeding}, no longer works in 26.2 because the baby is added after
 * that call returns. On a cancel the parents lose their love and wait out the breeding cooldown,
 * as on NeoForge. A handler may also hand back a different child.
 */
@Mixin(Animal.class)
public abstract class BabyEntitySpawnMixin {

    @WrapOperation(method = "spawnChildFromBreeding", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/animal/Animal;getBreedOffspring(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/AgeableMob;)Lnet/minecraft/world/entity/AgeableMob;"))
    private AgeableMob historystages$raise(Animal self, ServerLevel level, AgeableMob partnerMob,
                                          Operation<AgeableMob> original) {
        AgeableMob child = original.call(self, level, partnerMob);
        if (!Phases.wanted(BabyEntitySpawnEvent.class) || !(partnerMob instanceof Animal partner)) {
            return child;
        }
        BabyEntitySpawnEvent event = EventBus.post(new BabyEntitySpawnEvent(self, partner, child));
        if (event.isCanceled()) {
            self.setAge(6000);
            partner.setAge(6000);
            self.resetLove();
            partner.resetLove();
            return null;
        }
        return event.getChild();
    }
}
