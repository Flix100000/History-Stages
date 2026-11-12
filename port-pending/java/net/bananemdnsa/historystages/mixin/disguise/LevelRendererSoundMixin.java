package net.bananemdnsa.historystages.mixin.disguise;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bananemdnsa.historystages.client.disguise.DisguiseSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The break sound of level event 2001, which the server sends with the real block. Its particles
 * need nothing extra: they take their texture from the block model, which is already disguised. See {@link DisguiseSounds}.
 */
@Mixin(LevelRenderer.class)
public class LevelRendererSoundMixin {

    @WrapOperation(method = "levelEvent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/level/block/SoundType;"))
    private SoundType historystages$disguiseSound(BlockState state, LevelReader level, BlockPos pos,
                                                  Entity entity, Operation<SoundType> original) {
        return DisguiseSounds.soundType(state, level, pos, entity, original);
    }
}
