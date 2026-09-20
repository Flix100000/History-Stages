package net.bananemdnsa.historystages.mixin.event;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.living.MobSpawnEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises the "may this kind of mob spawn here at all" question, which vanilla asks before it
 * builds one.
 *
 * <p>Refusing here is cheaper than refusing the finished mob, and it does not count against the
 * spawn attempts for the chunk — so a zone that turns a mob off does not quietly make everything
 * else spawn less often.
 */
@Mixin(SpawnPlacements.class)
public abstract class SpawnPlacementCheckMixin {

    @Inject(method = "checkSpawnRules", at = @At("RETURN"), cancellable = true)
    private static <T extends Entity> void historystages$raise(
            EntityType<T> type, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos,
            RandomSource random, CallbackInfoReturnable<Boolean> cir) {
        MobSpawnEvent.SpawnPlacementCheck event = EventBus.post(
                new MobSpawnEvent.SpawnPlacementCheck(type, level, spawnType, pos));
        switch (event.getResult()) {
            case FAIL -> cir.setReturnValue(false);
            case SUCCEED -> cir.setReturnValue(true);
            case DEFAULT -> { }
        }
    }
}
