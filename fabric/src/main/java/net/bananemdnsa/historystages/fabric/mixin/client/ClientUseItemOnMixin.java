package net.bananemdnsa.historystages.fabric.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bananemdnsa.historystages.fabric.events.RightClickBlockState;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
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
 * The client half of the right-click-on-a-block gates, mirroring {@code ServerUseItemOnMixin}
 * (8ef8f5e3 on fabric-1.21.X). Fabric raises {@code UseBlockCallback} on the client too, so the
 * handlers answer there as well; without these gates the client predicts what the server then
 * refuses: the block appears for a moment and the held stack loses a count nothing gives back.
 *
 * <p>The gates sit in {@code performUseItemOn}, where vanilla does the work; {@code useItemOn}
 * only wraps it in a prediction, and the decision is scoped around that whole call.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class ClientUseItemOnMixin {

    @WrapMethod(method = "useItemOn")
    private InteractionResult historystages$scope(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult,
                                                  Operation<InteractionResult> original) {
        return RightClickBlockState.around(() -> original.call(player, hand, hitResult));
    }

    @WrapOperation(method = "performUseItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;useItemOn(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult historystages$gateBlockWithItem(BlockState state, ItemStack stack, Level level, Player player,
                                                              InteractionHand hand, BlockHitResult hitResult,
                                                              Operation<InteractionResult> original) {
        if (RightClickBlockState.blockRefused()) {
            return InteractionResult.PASS;
        }
        return original.call(state, stack, level, player, hand, hitResult);
    }

    @WrapOperation(method = "performUseItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;useWithoutItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult historystages$gateBlockWithoutItem(BlockState state, Level level, Player player,
                                                                 BlockHitResult hitResult,
                                                                 Operation<InteractionResult> original) {
        if (RightClickBlockState.blockRefused()) {
            return InteractionResult.PASS;
        }
        return original.call(state, level, player, hitResult);
    }

    @WrapOperation(method = "performUseItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult historystages$gateItem(ItemStack stack, UseOnContext context,
                                                     Operation<InteractionResult> original) {
        if (RightClickBlockState.itemRefused()) {
            return InteractionResult.PASS;
        }
        return original.call(stack, context);
    }
}
