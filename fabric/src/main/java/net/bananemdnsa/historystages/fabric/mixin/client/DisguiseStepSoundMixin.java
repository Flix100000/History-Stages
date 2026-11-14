package net.bananemdnsa.historystages.fabric.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bananemdnsa.historystages.client.disguise.DisguiseSounds;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Steps on a disguised block sound like the disguise (vanilla asks getSoundType()). */
@Mixin(Entity.class)
public class DisguiseStepSoundMixin {

    // One call site in each of the three methods; require = 3 so a lost one fails loudly.
    @WrapOperation(method = {"playCombinationStepSounds", "playMuffledStepSound", "playStepSound"}, require = 3,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
    private SoundType historystages$disguiseStep(BlockState state, Operation<SoundType> original) {
        return original.call(DisguiseSounds.heard(state, ((Entity) (Object) this).level()));
    }
}
