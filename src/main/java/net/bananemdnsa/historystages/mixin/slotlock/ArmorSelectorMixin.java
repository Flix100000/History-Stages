package net.bananemdnsa.historystages.mixin.slotlock;

import net.bananemdnsa.historystages.util.lock.SlotLocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A dispenser only dresses entities this selector accepts. Refusing here, rather than at the
 * equip itself, means the dispenser drops the armor as usual instead of swallowing it.
 */
@Mixin(EntitySelector.MobCanWearArmorEntitySelector.class)
public abstract class ArmorSelectorMixin {

    @Shadow @Final private ItemStack itemStack;

    @Inject(method = "test(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void historystages$test(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof Player player) || !SlotLocks.anyPlayerSlotLocks(player)) return;
        EquipmentSlot slot = player.getEquipmentSlotForItem(itemStack);
        if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) return;
        // Inventory armor indices run feet (36) to head (39), the same order as the slot index.
        if (SlotLocks.playerSlot(player, 36 + slot.getIndex()).blocks()) cir.setReturnValue(false);
    }
}
