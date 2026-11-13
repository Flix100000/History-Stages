package net.bananemdnsa.historystages.fabric.mixin.event;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bananemdnsa.historystages.fabric.events.RightClickBlockState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The server half of the right-click-on-a-block gates.
 *
 * <p>The event itself is raised from Fabric's {@code UseBlockCallback} (one listener per priority,
 * see {@code InteractionBridge}), which fires at the top of this method. A callback can only say
 * yes or no, but the event has a third answer: "the block may not react, the held item may still
 * be used", which is how a locked chest stays shut while a torch can still go on it. Those two
 * halves are gated here, where vanilla performs them.
 *
 * <p>A refused block answers PASS from {@code BlockState.useItemOn}: in 26.2 that skips the
 * fallback to {@code useWithoutItem} (TRY_WITH_EMPTY_HAND would ask for it). Not covered:
 * spectators, whose read-only menu opens from an earlier branch; a cancel still stops them.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerUseItemOnMixin {

    /** Opens a fresh decision for this click and closes it afterwards, whatever happens inside. */
    @WrapMethod(method = "useItemOn")
    private InteractionResult historystages$scope(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand,
                                                  BlockHitResult hitResult, Operation<InteractionResult> original) {
        return RightClickBlockState.around(() -> original.call(player, level, stack, hand, hitResult));
    }

    @WrapOperation(method = "useItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;useItemOn(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult historystages$gateBlockWithItem(BlockState state, ItemStack stack, Level level, Player player,
                                                              InteractionHand hand, BlockHitResult hitResult,
                                                              Operation<InteractionResult> original) {
        if (RightClickBlockState.blockRefused()) {
            return InteractionResult.PASS;
        }
        return original.call(state, stack, level, player, hand, hitResult);
    }

    @WrapOperation(method = "useItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;useWithoutItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult historystages$gateBlockWithoutItem(BlockState state, Level level, Player player,
                                                                 BlockHitResult hitResult,
                                                                 Operation<InteractionResult> original) {
        if (RightClickBlockState.blockRefused()) {
            return InteractionResult.PASS;
        }
        return original.call(state, level, player, hitResult);
    }

    /** Both call sites: vanilla reaches {@code useOn} from the ordinary branch and the creative one. */
    @WrapOperation(method = "useItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult historystages$gateItem(ItemStack stack, UseOnContext context,
                                                     Operation<InteractionResult> original) {
        if (RightClickBlockState.itemRefused()) {
            return InteractionResult.PASS;
        }
        return original.call(stack, context);
    }
}
