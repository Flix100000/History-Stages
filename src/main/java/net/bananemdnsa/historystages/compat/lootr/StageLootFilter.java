package net.bananemdnsa.historystages.compat.lootr;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.lock.LootLocks;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import noobanidus.mods.lootr.common.api.data.LootFiller;
import noobanidus.mods.lootr.common.api.filter.ILootrFilter;

import java.util.UUID;

/**
 * Strips staged items out of Lootr loot at the moment it is rolled for a player.
 *
 * <p>{@code LootLockHandler} can only see loot that arrives through a container window, and not
 * everything Lootr hands out does: a decorated pot drops its contents straight on the ground, and
 * so does a brushed suspicious block. Rolling is the one point every one of those paths shares,
 * and it is also the only point that names the player the copy belongs to.
 *
 * <p>This and its provider are the only places in the mod that import Lootr. Nothing references
 * either of them — Lootr's own {@code ServiceLoader} is what loads them — so a Lootr old enough to
 * lack the filter API never reads the service file, never links either class, and the mod runs as
 * it did before.
 */
public final class StageLootFilter implements ILootrFilter {

    /**
     * Filters run highest first, and the first one to return true stops the rest. A gate that a
     * cosmetic filter can switch off is not a gate, so this sits above the usual crowd — and it
     * never claims the loot itself.
     */
    @Override
    public int getPriority() {
        return 1000;
    }

    @Override
    public String getName() {
        return "historystages:stage_locks";
    }

    @Override
    public boolean mutate(ObjectArrayList<ItemStack> loot, LootFiller.LootFillerState state,
                          LootContext context, RandomSource random) {
        // Lootr hooks LootTable.fill, so this runs for every loot roll in the game, world
        // generation included. A null state is how a foreign roll announces itself: without this
        // line every vanilla chest would be filtered against whichever player Lootr saw last.
        if (state == null) return false;

        Player player = state.player();
        UUID playerUuid = player != null ? player.getUUID() : null;

        int replacedCount = 0;
        for (int i = loot.size() - 1; i >= 0; i--) {
            ItemStack stack = loot.get(i);
            if (stack.isEmpty() || !LootLocks.isLocked(stack, playerUuid)) continue;

            ItemStack replacement = LootLocks.replacementFor(stack.getCount());
            // An empty stack still costs a slot in LootTable.fill, so drop the entry instead.
            if (replacement.isEmpty()) {
                loot.remove(i);
            } else {
                loot.set(i, replacement);
            }
            replacedCount++;
        }

        if (replacedCount > 0) {
            DebugLogger.runtime("Loot Lock", player != null ? player.getName().getString() : "-",
                    "Replaced " + replacedCount + " locked item(s) while rolling Lootr loot [action: loot]");
        }

        return false;
    }
}
