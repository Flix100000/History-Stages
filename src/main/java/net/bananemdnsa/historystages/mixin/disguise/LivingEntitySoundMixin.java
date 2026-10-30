package net.bananemdnsa.historystages.mixin.disguise;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bananemdnsa.historystages.client.disguise.DisguiseSounds;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The thud of landing on a block. See {@link DisguiseSounds}.
 */
@Mixin(LivingEntity.class)
public class LivingEntitySoundMixin {

    @WrapOperation(method = "playBlockFallSound", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
    private SoundType historystages$disguiseSound(BlockState state, Operation<SoundType> original) {
        return DisguiseSounds.soundType(state, ((LivingEntity) (Object) this).level().isClientSide(), original);
    }
}
