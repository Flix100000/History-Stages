package net.bananemdnsa.historystages.fabric.mixin.event;

import net.bananemdnsa.historystages.fabric.events.Phases;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.living.MobSpawnEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises "may this mob stand here" at RETURN, so vanilla has had its say and the three answers
 * mean what they should: FAIL refuses a spot vanilla allowed, SUCCEED allows one it refused,
 * DEFAULT leaves the verdict alone.
 */
@Mixin(NaturalSpawner.class)
public abstract class MobSpawnChecksMixin {

    @Inject(method = "isValidPositionForMob", at = @At("RETURN"), cancellable = true)
    private static void historystages$raise(ServerLevel level, Mob mob, double distance,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (!Phases.wanted(MobSpawnEvent.PositionCheck.class)) {
            return;
        }
        MobSpawnEvent.PositionCheck event = EventBus.post(new MobSpawnEvent.PositionCheck(
                mob, level, EntitySpawnReason.NATURAL, mob.getX(), mob.getY(), mob.getZ()));
        switch (event.getResult()) {
            case FAIL -> cir.setReturnValue(false);
            case SUCCEED -> cir.setReturnValue(true);
            case DEFAULT -> { }
        }
    }
}
