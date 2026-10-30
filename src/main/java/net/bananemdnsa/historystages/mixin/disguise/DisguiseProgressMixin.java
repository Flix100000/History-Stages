package net.bananemdnsa.historystages.mixin.disguise;

import net.bananemdnsa.historystages.events.lock.DisguiseBreakHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A disguised block breaks at the disguise's pace. Both sides call this with their own view of
 * the player's stages, so the cracks on screen and the server's break land on the same tick.
 *
 * <p>Handing the whole progress over rather than adjusting the speed is what NeoForge's three
 * events add up to: the disguise's hardness, its tool requirement and, for something unbreakable,
 * a progress of zero. The disguise's own progress still goes through the player's break speed, so
 * a lock on the disguise slows it the way it would slow the real thing.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class DisguiseProgressMixin {

    @Inject(method = "getDestroyProgress", at = @At("HEAD"), cancellable = true)
    private void historystages$disguise(Player player, BlockGetter level, BlockPos pos,
                                        CallbackInfoReturnable<Float> cir) {
        BlockState fake = DisguiseBreakHandler.breaksAs(player, (BlockState) (Object) this);
        if (fake != null) {
            cir.setReturnValue(fake.getDestroyProgress(player, level, pos));
        }
    }
}
