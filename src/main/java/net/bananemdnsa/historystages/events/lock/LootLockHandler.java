package net.bananemdnsa.historystages.events.lock;

import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.lock.LootLocks;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.UUID;

@EventBusSubscriber(modid = "historystages")
public class LootLockHandler {

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity().level().isClientSide()) return;

        if (event.getContainer().slots.isEmpty()) return;
        Container container = event.getContainer().slots.get(0).container;

        if (container == null) return;

        if (!container.getClass().getName().toLowerCase().contains("lootr")) return;

        // Lootr hands every player their own copy of the loot, so the container being opened
        // belongs to this player alone and may be stripped against their individual stages.
        UUID playerUuid = event.getEntity().getUUID();

        int replacedCount = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;

            if (LootLocks.isLocked(stack, playerUuid)) {
                container.setItem(i, LootLocks.replacementFor(stack.getCount()));
                replacedCount++;
            }
        }

        if (replacedCount > 0) {
            DebugLogger.runtime("Loot Lock", event.getEntity().getName().getString(),
                    "Replaced " + replacedCount + " locked item(s) in Lootr container [action: loot]");
            event.getContainer().broadcastChanges();
        }
    }
}
