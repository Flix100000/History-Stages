package net.bananemdnsa.historystages.mixin.disguise;

import net.bananemdnsa.historystages.client.disguise.ClientDisguises;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Draws a disguised block with its disguise's model. Every block model lookup ends here — chunk
 * meshes (vanilla and Sodium/Embeddium alike), break and walk particles via
 * {@code getParticleIcon} — so one hook covers them all. Runs on chunk-build threads, which is why
 * {@link ClientDisguises#view} only reads a finished table.
 */
@Mixin(BlockModelShaper.class)
public class BlockModelShaperMixin {

    @ModifyVariable(method = "getBlockModel", at = @At("HEAD"), argsOnly = true)
    private BlockState historystages$disguise(BlockState state) {
        return ClientDisguises.view(state);
    }
}
