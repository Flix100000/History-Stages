package net.bananemdnsa.historystages.mixin.event;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.bananemdnsa.historystages.platform.DeathDropCapture;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.living.LivingDropsEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

/**
 * Raises the death-drops event with the whole list in one piece.
 *
 * <p>Vanilla drops item by item from three separate methods, so there is no single list to hand
 * over. The drops are held back for the duration of this call instead, shown to the handlers
 * together, and then added. A lock that removes one therefore removes it before it ever exists in
 * the world, rather than deleting it again a tick later in front of the player.
 *
 * <p>Wrapping the method rather than injecting at its end, so the capture is released in a
 * finally: an exception out of any loot table would otherwise leave it open, and from that point
 * on every drop on the server would vanish into it.
 */
@Mixin(LivingEntity.class)
public abstract class LivingDropsMixin {

    @WrapMethod(method = "dropAllDeathLoot")
    private void historystages$captureDrops(ServerLevel level, DamageSource source, Operation<Void> original) {
        LivingEntity self = (LivingEntity) (Object) this;
        List<ItemEntity> drops;
        DeathDropCapture.begin(self);
        try {
            original.call(level, source);
        } finally {
            drops = DeathDropCapture.end();
        }

        LivingDropsEvent event = EventBus.post(new LivingDropsEvent(self, source, drops));
        if (event.isCanceled()) {
            return;
        }
        for (ItemEntity drop : drops) {
            level.addFreshEntity(drop);
        }
    }
}
