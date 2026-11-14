package net.bananemdnsa.historystages.fabric.mixin.event;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.bananemdnsa.historystages.fabric.events.BreakingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Destroy progress knows where the block is; the speed and harvest events it triggers read it.
 * On the state, not the block: a block that overrides getDestroyProgress without calling super
 * still gets its position through.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BreakingBlockScopeMixin {

    @WrapMethod(method = "getDestroyProgress")
    private float historystages$scope(Player player, BlockGetter level, BlockPos pos, Operation<Float> original) {
        return BreakingBlock.around(level, pos, () -> original.call(player, level, pos));
    }
}
