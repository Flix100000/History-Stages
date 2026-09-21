package net.bananemdnsa.historystages.mixin.event;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.player.PlayerInteractEvent;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises the right-click-on-a-block event on the server, with all three of its answers.
 *
 * <p>Fabric's own callback cannot carry this one. Its answer is yes or no, while this event has a
 * third state that means "do not let the block itself react, but still let the held item be
 * used" — which is how a locked chest stays shut while a torch can still be placed on it. So the
 * event is raised at the top of the method and the two halves are gated where vanilla performs
 * them, rather than by reimplementing the method.
 *
 * <p>Gating the block half returns SKIP_DEFAULT_BLOCK_INTERACTION rather than PASS: PASS would
 * send vanilla on to {@code useWithoutItem}, which is the very thing being refused.
 *
 * <p><strong>Not covered:</strong> spectators. Vanilla answers a spectator's right click from its
 * own branch further up, which opens a read-only view of the block's menu and never reaches
 * either half gated here. A cancelled event still stops it; a plain block-use denial does not.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerUseItemOnMixin {

    /**
     * The event for the call in progress. Safe as a field because this class is per player and
     * the server runs it on one thread; it is overwritten at the start of every call, so a value
     * left behind by a call that threw cannot be read by the next one.
     */
    @Unique
    private PlayerInteractEvent.RightClickBlock historystages$current;

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void historystages$raise(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand,
                                     BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        PlayerInteractEvent.RightClickBlock event = EventBus.post(new PlayerInteractEvent.RightClickBlock(
                player, hand, hitResult.getBlockPos(), hitResult.getDirection()));
        historystages$current = event;
        if (event.isCanceled()) {
            historystages$current = null;
            cir.setReturnValue(event.getCancellationResult());
        }
    }

    @WrapOperation(method = "useItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;useItemOn(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/ItemInteractionResult;"))
    private ItemInteractionResult historystages$gateBlockWithItem(
            BlockState state, ItemStack stack, Level level, Player player, InteractionHand hand,
            BlockHitResult hitResult, Operation<ItemInteractionResult> original) {
        if (historystages$blockRefused()) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return original.call(state, stack, level, player, hand, hitResult);
    }

    @WrapOperation(method = "useItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;useWithoutItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult historystages$gateBlockWithoutItem(
            BlockState state, Level level, Player player, BlockHitResult hitResult,
            Operation<InteractionResult> original) {
        if (historystages$blockRefused()) {
            return InteractionResult.PASS;
        }
        return original.call(state, level, player, hitResult);
    }

    @WrapOperation(method = "useItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult historystages$gateItem(
            ItemStack stack, UseOnContext context, Operation<InteractionResult> original) {
        if (historystages$current != null && historystages$current.getUseItem() == TriState.FALSE) {
            return InteractionResult.PASS;
        }
        return original.call(stack, context);
    }

    @Unique
    private boolean historystages$blockRefused() {
        return historystages$current != null && historystages$current.getUseBlock() == TriState.FALSE;
    }
}
