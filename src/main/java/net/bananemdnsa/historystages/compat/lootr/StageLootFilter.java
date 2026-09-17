package net.bananemdnsa.historystages.compat.lootr;

import java.util.UUID;

import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.lock.LootLocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Strips staged items out of Lootr loot at the moment it is rolled for a player.
 *
 * <p>Not everything Lootr hands out arrives through a container window: a decorated pot drops its
 * contents straight on the ground, and so does a brushed suspicious block. Rolling is the one point
 * every one of those paths shares, and it is also the only point that names the player the copy
 * belongs to.
 *
 * <p>Nothing strips a container when it is opened any more. By then the inventory may hold what a
 * player put there themselves, and Lootr keeps loot and stored items in the same per-player
 * inventory, so there is no telling the two apart (#124). The cost is that loot rolled before a
 * stage was locked stays where it is; taking it out and using it is left to the other locks.
 *
 * <p>Called from {@code mixin/lootr/ChestDataMixin}, which is where Lootr fills a fresh copy.
 * The inventory handed in has just been created and holds nothing but that roll.
 */
public final class StageLootFilter {

    private StageLootFilter() {}

    /** Replaces every locked stack in a freshly rolled copy. Returns how many were touched. */
    public static int strip(Container rolled, ServerPlayer player) {
        UUID playerUuid = player != null ? player.getUUID() : null;

        int replacedCount = 0;
        for (int i = 0; i < rolled.getContainerSize(); i++) {
            ItemStack stack = rolled.getItem(i);
            if (stack.isEmpty() || !LootLocks.isLocked(stack, playerUuid)) continue;

            rolled.setItem(i, LootLocks.replacementFor(stack.getCount()));
            replacedCount++;
        }

        if (replacedCount > 0) {
            DebugLogger.runtime("Loot Lock", player != null ? player.getName().getString() : "-",
                    "Replaced " + replacedCount + " locked item(s) while rolling Lootr loot [action: loot]");
        }
        return replacedCount;
    }
}
