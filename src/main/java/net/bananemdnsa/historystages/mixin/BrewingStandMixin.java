package net.bananemdnsa.historystages.mixin;

import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.minecraft.core.NonNullList;
import net.bananemdnsa.historystages.data.lock.engine.StackContents;
import net.bananemdnsa.historystages.data.lock.engine.StackContentsReader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the brewing stand from brewing a potion with a locked effect.
 *
 * <p>Hooked on {@code isBrewable} rather than on the brew itself. Refusing at the brew would
 * leave the stand starting over and burning a blaze powder every 20 seconds; refusing here means
 * it never starts, nothing is consumed, and the bubbles never show.
 *
 * <p>On Forge the stand asks {@link BrewingRecipeRegistry} for both the check and the result, so
 * mod brewing recipes are covered the same way vanilla ones are.
 *
 * <p>Global stages only. The stand brews with nobody standing at it, so there is no player whose
 * individual stages could count. A potion locked by an individual stage is still locked in that
 * player's hands.
 */
@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandMixin {

    @Inject(method = "isBrewable", at = @At("RETURN"), cancellable = true)
    private static void historystages$refuseLockedEffects(NonNullList<ItemStack> items,
                                                         CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        ItemStack ingredient = items.get(3);
        for (int i = 0; i < 3; i++) {
            ItemStack input = items.get(i);
            if (input.isEmpty()) continue;
            ItemStack output = BrewingRecipeRegistry.getOutput(input, ingredient);
            if (!output.isEmpty() && historystages$carriesLockedEffect(output)) {
                cir.setReturnValue(false);
                return;
            }
        }
    }

    @Unique
    private static boolean historystages$carriesLockedEffect(ItemStack potion) {
        // The result's item type, so an entry that spares splash potions lets them be brewed.
        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(potion.getItem()).toString();
        for (StackContents.Levelled effect : StackContentsReader.of(potion).effects()) {
            if (StageLockHelper.isEffectLockedForServer(effect.id(), effect.level(), itemId)) return true;
        }
        return false;
    }
}
