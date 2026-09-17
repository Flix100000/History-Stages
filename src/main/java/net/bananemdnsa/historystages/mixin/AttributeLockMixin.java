package net.bananemdnsa.historystages.mixin;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.util.lock.LockGate;
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
 * Forge's ItemAttributeModifierEvent, which carries no entity context.
 *
 * <p>Wraps the new-item apply call in {@code LivingEntity.collectEquipmentChanges} — the second
 * {@code ItemStack.getAttributeModifiers} invocation (ordinal 1); the first (ordinal 0) is the
 * old-item removal and is left untouched so modifiers are still cleaned up on slot changes. An
 * empty multimap for a locked item skips its bonuses entirely.</p>
 *
 * <p>A wrapper rather than a redirect, because a redirect owns the call site alone and drops
 * whichever other mod loses the race — attribute and equipment mods hook this method too.</p>
 */
@Mixin(LivingEntity.class)
public class AttributeLockMixin {

    @WrapOperation(
            method = "collectEquipmentChanges",
            at = @At(value = "INVOKE", ordinal = 1,
                    target = "Lnet/minecraft/world/item/ItemStack;getAttributeModifiers(Lnet/minecraft/world/entity/EquipmentSlot;)Lcom/google/common/collect/Multimap;"))
    private Multimap<Attribute, AttributeModifier> historystages$skipLockedAttributeModifiers(
            ItemStack stack, EquipmentSlot slot,
            Operation<Multimap<Attribute, AttributeModifier>> original) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player player
                && (Config.GAMEPLAY.lockItemUsage.get() || Config.GAMEPLAY.individualLockItemUsage.get())
                && LockGate.isActionLocked(stack, player, "use",
                        Config.GAMEPLAY.lockItemUsage, Config.GAMEPLAY.individualLockItemUsage)) {
            // Locked: the item hands out no modifiers at all, so the operation is skipped
            // outright. Anything another mod wrapped inside it goes with it, which is the point —
            // a locked item must not gain bonuses through someone else's hook either.
            return ImmutableMultimap.of();
        }
        return original.call(stack, slot);
    }
}
