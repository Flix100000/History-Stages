package net.bananemdnsa.historystages.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.player.PlayerInteractEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
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
 * The client half of the right-click-on-a-block event, mirroring {@code ServerUseItemOnMixin}.
 *
 * <p>The event has to be raised on both sides, and the handlers are written for that — several of
 * them answer the same question twice and guard their logging and packets with
 * {@code isClientSide()}. Without this half only the server refuses, and the client goes ahead and
 * predicts: the block appears for a moment and the held stack loses a count. The server's answer
 * takes the block away again, but nothing tells the client about the item, so the player watches a
 * block vanish and is one short.
 *
 * <p>The gates sit in {@code performUseItemOn} rather than in {@code useItemOn}, because that is
 * where vanilla does the work — {@code useItemOn} only wraps it in a prediction. Refusing the whole
 * method instead would take the packet with it, and then a locked <em>item</em> in hand would also
 * stop the block underneath from opening, which is exactly the distinction this event exists for.
 *
 * <p>Answers are mapped as on the server: a refused block interaction returns
 * SKIP_DEFAULT_BLOCK_INTERACTION so vanilla does not fall through to {@code useWithoutItem}, and a
 * refused item returns PASS.
 */
@Environment(EnvType.CLIENT)
@Mixin(MultiPlayerGameMode.class)
public abstract class ClientUseItemOnMixin {

    /**
     * The event for the click in progress. A client has one game mode and one thread for this, and
     * the value is replaced at the start of every click, so nothing can read a stale one.
     */
    @Unique
    private PlayerInteractEvent.RightClickBlock historystages$current;

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void historystages$raise(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult,
                                     CallbackInfoReturnable<InteractionResult> cir) {
        PlayerInteractEvent.RightClickBlock event = EventBus.post(new PlayerInteractEvent.RightClickBlock(
                player, hand, hitResult.getBlockPos(), hitResult.getDirection()));
        historystages$current = event;
        if (event.isCanceled()) {
            historystages$current = null;
            cir.setReturnValue(event.getCancellationResult());
        }
    }

    @WrapOperation(method = "performUseItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;useItemOn(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/ItemInteractionResult;"))
    private ItemInteractionResult historystages$gateBlockWithItem(
            BlockState state, ItemStack stack, Level level, Player player, InteractionHand hand,
            BlockHitResult hitResult, Operation<ItemInteractionResult> original) {
        if (historystages$blockRefused()) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return original.call(state, stack, level, player, hand, hitResult);
    }

    @WrapOperation(method = "performUseItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;useWithoutItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult historystages$gateBlockWithoutItem(
            BlockState state, Level level, Player player, BlockHitResult hitResult,
            Operation<InteractionResult> original) {
        if (historystages$blockRefused()) {
            return InteractionResult.PASS;
        }
        return original.call(state, level, player, hitResult);
    }

    /**
     * Both call sites are wrapped, not just one: vanilla reaches {@code useOn} from the ordinary
     * branch and again from the creative one.
     */
    @WrapOperation(method = "performUseItemOn", at = @At(value = "INVOKE",
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
