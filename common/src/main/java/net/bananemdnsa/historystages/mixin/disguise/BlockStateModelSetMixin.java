package net.bananemdnsa.historystages.mixin.disguise;

import net.bananemdnsa.historystages.client.disguise.ClientDisguises;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * A disguised block is drawn with its disguise's model: chunk meshes, block particles and the
 * breaking overlay all look the state up here. 1.21.1's {@code BlockModelShaper.getBlockModel}.
 */
@Mixin(BlockStateModelSet.class)
public class BlockStateModelSetMixin {

    @ModifyVariable(method = "get", at = @At("HEAD"), argsOnly = true)
    private BlockState historystages$disguise(BlockState state) {
        return ClientDisguises.view(state);
    }
}
