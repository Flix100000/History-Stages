package net.bananemdnsa.historystages.fabric.mixin.event;

import net.bananemdnsa.historystages.fabric.events.Phases;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.living.FinalizeSpawnEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises the last word before a mob is finished.
 *
 * <p>NeoForge raises it in the callers and skips adding a refused mob; vanilla's callers do not
 * ask, so a refused mob is discarded here and is already removed when something tries to add it.
 * The join-level gate catches the same refusal again, on purpose: this method has callers other
 * than the spawner.
 */
@Mixin(Mob.class)
public abstract class FinalizeSpawnMixin {

    @Inject(method = "finalizeSpawn", at = @At("HEAD"), cancellable = true)
    private void historystages$raise(ServerLevelAccessor level, DifficultyInstance difficulty,
                                     EntitySpawnReason spawnType, SpawnGroupData groupData,
                                     CallbackInfoReturnable<SpawnGroupData> cir) {
        if (!Phases.wanted(FinalizeSpawnEvent.class)) {
            return;
        }
        Mob mob = (Mob) (Object) this;
        FinalizeSpawnEvent event = EventBus.post(new FinalizeSpawnEvent(
                mob, level, mob.getX(), mob.getY(), mob.getZ(), spawnType));
        if (event.isSpawnCancelled()) {
            mob.discard();
        }
        if (event.isCanceled() || event.isSpawnCancelled()) {
            cir.setReturnValue(groupData);
        }
    }
}
