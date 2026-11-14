package net.bananemdnsa.historystages.neoforge.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bananemdnsa.historystages.client.disguise.DisguiseSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Steps on a disguised block sound like the disguise. NeoForge plays steps through the state. */
@Mixin(Entity.class)
public class EntityStepSoundMixin {

    // One call site in each of the three methods; require = 3 so a lost one fails loudly.
    @WrapOperation(method = {"playCombinationStepSounds", "playMuffledStepSound", "playStepSound"}, require = 3,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;"
                    + "playStepSound(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;FF)V"))
    private void historystages$disguiseStep(BlockState state, Level level, BlockPos pos, Entity entity,
                                            float volume, float pitch, Operation<Void> original) {
        original.call(DisguiseSounds.heard(state, level), level, pos, entity, volume, pitch);
    }
}
