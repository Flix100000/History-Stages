package net.bananemdnsa.historystages.fabric.mixin.event;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bananemdnsa.historystages.platform.PlantHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises the bonemeal event where NeoForge does: before {@code growCrop} grows anything or uses
 * the bonemeal up.
 *
 * <p>Two hooks because vanilla's {@code growCrop} takes no player. The use path asks first, with
 * the player, so a refusal can tell them why; the head of {@code growCrop} catches dispensers and
 * mods that call it directly, with no player.
 */
@Mixin(BoneMealItem.class)
public abstract class BonemealMixin {

    @WrapOperation(method = "useOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/BoneMealItem;growCrop(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean historystages$raiseWithPlayer(ItemStack stack, Level level, BlockPos pos,
                                                  Operation<Boolean> original, UseOnContext context) {
        if (!PlantHooks.mayBonemeal(context.getPlayer(), level, pos, stack)) return false;
        return original.call(stack, level, pos);
    }

    @Inject(method = "growCrop", at = @At("HEAD"), cancellable = true)
    private static void historystages$raise(ItemStack stack, Level level, BlockPos pos,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (!PlantHooks.mayBonemeal(null, level, pos, stack)) cir.setReturnValue(false);
    }
}
