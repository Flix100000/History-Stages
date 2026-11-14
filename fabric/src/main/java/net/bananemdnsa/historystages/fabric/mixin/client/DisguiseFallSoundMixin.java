package net.bananemdnsa.historystages.fabric.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bananemdnsa.historystages.client.disguise.DisguiseSounds;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Landing on a disguised block sounds like the disguise. */
@Mixin(LivingEntity.class)
public class DisguiseFallSoundMixin {

    @WrapOperation(method = "playBlockFallSound", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
    private SoundType historystages$disguiseFall(BlockState state, Operation<SoundType> original) {
        return original.call(DisguiseSounds.heard(state, ((LivingEntity) (Object) this).level()));
    }
}
