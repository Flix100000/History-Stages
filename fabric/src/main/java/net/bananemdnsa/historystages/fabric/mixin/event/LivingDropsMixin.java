package net.bananemdnsa.historystages.fabric.mixin.event;

import java.util.List;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.bananemdnsa.historystages.fabric.events.DeathDropCapture;
import net.bananemdnsa.historystages.fabric.events.Phases;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.living.LivingDropsEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Raises the death-drops event with the whole list in one piece. Vanilla drops item by item from
 * several methods, so the drops are held back for this call (see {@link DeathDropCapture}), shown
 * to the handlers together, then added.
 *
 * <p>Wrapping the method rather than injecting at its end, so the capture is released in a
 * finally: an exception from a loot table would otherwise leave it open, and every later drop on
 * the server would vanish into it.
 */
@Mixin(LivingEntity.class)
public abstract class LivingDropsMixin {

    @WrapMethod(method = "dropAllDeathLoot")
    private void historystages$captureDrops(ServerLevel level, DamageSource source, Operation<Void> original) {
        if (!Phases.wanted(LivingDropsEvent.class)) {
            original.call(level, source);
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        List<ItemEntity> drops;
        DeathDropCapture.begin(self);
        try {
            original.call(level, source);
        } finally {
            drops = DeathDropCapture.end();
        }
        if (EventBus.post(new LivingDropsEvent(self, source, drops)).isCanceled()) {
            return;
        }
        for (ItemEntity drop : drops) {
            level.addFreshEntity(drop);
        }
    }
}
