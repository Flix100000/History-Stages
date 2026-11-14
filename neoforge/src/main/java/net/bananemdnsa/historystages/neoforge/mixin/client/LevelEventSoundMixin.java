package net.bananemdnsa.historystages.neoforge.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bananemdnsa.historystages.client.disguise.DisguiseSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.LevelEventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Breaking a disguised block (the break event) sounds like the disguise. NeoForge asks its three-argument getSoundType here. */
@Mixin(LevelEventHandler.class)
public class LevelEventSoundMixin {

    @WrapOperation(method = "levelEvent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/level/block/SoundType;"))
    private SoundType historystages$disguiseSound(BlockState state, LevelReader level, BlockPos pos, Entity entity,
                                                  Operation<SoundType> original) {
        return original.call(DisguiseSounds.heard(state, level), level, pos, entity);
    }
}
