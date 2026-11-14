package net.bananemdnsa.historystages.mixin.disguise;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.bananemdnsa.historystages.client.disguise.DisguiseSounds;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A disguised block steps, falls and breaks with its disguise's sounds.
 *
 * <p>1.21.1 wrapped the four call sites (steps, falling, mining, the break event). In 26.2 they
 * differ per loader: vanilla asks {@code BlockState.getSoundType()}, NeoForge asks its own
 * three-argument extension and plays steps through {@code playStepSound}. Both end in this method.
 * A block that overrides it without calling super keeps its real sound.
 */
@Mixin(BlockBehaviour.class)
public class BlockSoundMixin {

    @ModifyReturnValue(method = "getSoundType", at = @At("RETURN"))
    private SoundType historystages$disguiseSound(SoundType original, BlockState state) {
        return DisguiseSounds.soundType(state, original);
    }
}
