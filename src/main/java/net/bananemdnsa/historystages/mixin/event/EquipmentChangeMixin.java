package net.bananemdnsa.historystages.mixin.event;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Raises one equipment-change event per slot that actually changed, which is how a lock notices
 * a gated piece of armour being put on.
 *
 * <p>Vanilla has already worked out the difference by this point and hands over only the slots
 * that moved, so nothing here has to compare anything. The previous stack is read back off the
 * entity before vanilla overwrites it.
 */
@Mixin(LivingEntity.class)
public abstract class EquipmentChangeMixin {

    @Inject(method = "handleEquipmentChanges", at = @At("HEAD"))
    private void historystages$raise(Map<EquipmentSlot, ItemStack> changes, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        for (Map.Entry<EquipmentSlot, ItemStack> change : changes.entrySet()) {
            EventBus.post(new LivingEquipmentChangeEvent(
                    self, change.getKey(), self.getItemBySlot(change.getKey()), change.getValue()));
        }
    }
}
