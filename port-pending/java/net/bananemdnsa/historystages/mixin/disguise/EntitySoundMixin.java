package net.bananemdnsa.historystages.mixin.disguise;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bananemdnsa.historystages.client.disguise.DisguiseSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Step sounds of the local player. See {@link DisguiseSounds}.
 */
@Mixin(Entity.class)
public class EntitySoundMixin {

    @WrapOperation(method = {"playCombinationStepSounds", "playMuffledStepSound", "playStepSound"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/level/block/SoundType;"))
    private SoundType historystages$disguiseSound(BlockState state, LevelReader level, BlockPos pos,
                                                  Entity entity, Operation<SoundType> original) {
        return DisguiseSounds.soundType(state, level, pos, entity, original);
    }
}
