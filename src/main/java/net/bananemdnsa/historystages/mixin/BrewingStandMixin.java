package net.bananemdnsa.historystages.mixin;

import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
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
 * <p>Global stages only. The stand brews with nobody standing at it, so there is no player whose
 * individual stages could count. A potion locked by an individual stage is still locked in that
 * player's hands.
 */
@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandMixin {

    @Inject(method = "isBrewable", at = @At("RETURN"), cancellable = true)
    private static void historystages$refuseLockedEffects(PotionBrewing brewing, NonNullList<ItemStack> items,
                                                         CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        ItemStack ingredient = items.get(3);
        for (int i = 0; i < 3; i++) {
            ItemStack input = items.get(i);
            if (input.isEmpty() || !brewing.hasMix(input, ingredient)) continue;
            if (historystages$carriesLockedEffect(brewing.mix(ingredient, input))) {
                cir.setReturnValue(false);
                return;
            }
        }
    }

    @Unique
    private static boolean historystages$carriesLockedEffect(ItemStack potion) {
        PotionContents contents = potion.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return false;
        // The result's item type, so an entry that spares splash potions lets them be brewed.
        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(potion.getItem()).toString();
        for (MobEffectInstance effect : contents.getAllEffects()) {
            String id = effect.getEffect().unwrapKey().map(key -> key.location().toString()).orElse(null);
            if (id != null && StageLockHelper.isEffectLockedForServer(id, effect.getAmplifier() + 1, itemId)) {
                return true;
            }
        }
        return false;
    }
}
