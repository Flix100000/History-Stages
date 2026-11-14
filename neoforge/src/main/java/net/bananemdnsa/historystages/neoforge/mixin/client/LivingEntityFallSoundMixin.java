package net.bananemdnsa.historystages.neoforge.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bananemdnsa.historystages.client.disguise.DisguiseSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Landing on a disguised block sounds like the disguise. */
@Mixin(LivingEntity.class)
public class LivingEntityFallSoundMixin {

    @WrapOperation(method = "playBlockFallSound", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;"
                    + "playFallSound(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/LivingEntity;)V"))
    private void historystages$disguiseFall(BlockState state, Level level, BlockPos pos, LivingEntity entity,
                                            Operation<Void> original) {
        original.call(DisguiseSounds.heard(state, level), level, pos, entity);
    }
}
