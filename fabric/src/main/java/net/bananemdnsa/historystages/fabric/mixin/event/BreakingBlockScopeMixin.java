package net.bananemdnsa.historystages.fabric.mixin.event;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.bananemdnsa.historystages.fabric.events.BreakingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/** Destroy progress knows where the block is; the speed and harvest events it triggers read it. */
@Mixin(BlockBehaviour.class)
public abstract class BreakingBlockScopeMixin {

    @WrapMethod(method = "getDestroyProgress")
    private float historystages$scope(BlockState state, Player player, BlockGetter level, BlockPos pos,
                                      Operation<Float> original) {
        return BreakingBlock.around(level, pos, () -> original.call(state, player, level, pos));
    }
}
