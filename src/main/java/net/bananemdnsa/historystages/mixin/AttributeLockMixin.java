package net.bananemdnsa.historystages.mixin;

import java.util.function.BiConsumer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.util.lock.LockGate;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Suppresses a stage-locked item's attribute modifiers for the holding player, so a locked
 * weapon/wand grants none of its bonuses (attack damage, Spell Power, etc.) while it stays
 * inert in hand. Player-aware, so individual (per-player) stage locks are respected — unlike
 * NeoForge's ItemAttributeModifierEvent, which carries no entity context.
 *
 * <p>Wraps the first {@code ItemStack.forEachModifier} call in {@code collectEquipmentChanges}
 * (the new-item apply path). The second call (old-item removal) is left untouched so modifiers
 * are still cleaned up on slot changes.</p>
 *
 * <p>A wrapper rather than a redirect, because a redirect owns the call site alone and drops
 * whichever other mod loses the race — attribute and equipment mods hook this method too.</p>
 */
@Mixin(LivingEntity.class)
public class AttributeLockMixin {

    @WrapOperation(
            method = "collectEquipmentChanges",
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Lnet/minecraft/world/item/ItemStack;forEachModifier(Lnet/minecraft/world/entity/EquipmentSlot;Ljava/util/function/BiConsumer;)V"))
    private void historystages$skipLockedAttributeModifiers(
            ItemStack stack, EquipmentSlot slot,
            BiConsumer<Holder<Attribute>, AttributeModifier> consumer, Operation<Void> original) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player player
                && (Config.GAMEPLAY.lockItemUsage.get() || Config.GAMEPLAY.individualLockItemUsage.get())
                && LockGate.isActionLocked(stack, player, "use",
                        Config.GAMEPLAY.lockItemUsage, Config.GAMEPLAY.individualLockItemUsage)) {
            // Locked: the item hands out no modifiers at all, so the operation is skipped
            // outright. Anything another mod wrapped inside it goes with it, which is the point —
            // a locked item must not gain bonuses through someone else's hook either.
            return;
        }
        original.call(stack, slot, consumer);
    }
}
