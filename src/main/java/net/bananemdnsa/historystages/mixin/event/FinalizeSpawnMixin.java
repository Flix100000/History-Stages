package net.bananemdnsa.historystages.mixin.event;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.living.FinalizeSpawnEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises the last word before a mob is finished.
 *
 * <p>Keeping the mob out of the world takes an extra step here. NeoForge patches the callers to
 * check the event and skip adding it; vanilla's callers do not, so a refused mob is discarded
 * instead and is already removed by the time anything tries to add it.
 *
 * <p>The gate on entities joining the level catches the same refusal a second time. That
 * redundancy is deliberate: this method has several callers and only one of them is the spawner.
 */
@Mixin(Mob.class)
public abstract class FinalizeSpawnMixin {

    @Inject(method = "finalizeSpawn", at = @At("HEAD"), cancellable = true)
    private void historystages$raise(ServerLevelAccessor level, DifficultyInstance difficulty,
                                     MobSpawnType spawnType, SpawnGroupData groupData,
                                     CallbackInfoReturnable<SpawnGroupData> cir) {
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
