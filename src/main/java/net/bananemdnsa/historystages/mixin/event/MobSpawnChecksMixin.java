package net.bananemdnsa.historystages.mixin.event;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.living.MobSpawnEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises the "may this mob stand here" question.
 *
 * <p>At RETURN rather than HEAD so vanilla has already had its say, and the three answers then
 * mean what they should: FAIL refuses a spot vanilla would have allowed, SUCCEED allows one it
 * refused, and DEFAULT leaves the verdict untouched. A plain cancel could only ever express the
 * first of those, and a zone wanting to permit a spawn would have no way to say so.
 */
@Mixin(NaturalSpawner.class)
public abstract class MobSpawnChecksMixin {

    @Inject(method = "isValidPositionForMob", at = @At("RETURN"), cancellable = true)
    private static void historystages$raise(ServerLevel level, Mob mob, double distance,
                                            CallbackInfoReturnable<Boolean> cir) {
        MobSpawnEvent.PositionCheck event = EventBus.post(new MobSpawnEvent.PositionCheck(
                mob, level, MobSpawnType.NATURAL, mob.getX(), mob.getY(), mob.getZ()));
        switch (event.getResult()) {
            case FAIL -> cir.setReturnValue(false);
            case SUCCEED -> cir.setReturnValue(true);
            case DEFAULT -> { }
        }
    }
}
