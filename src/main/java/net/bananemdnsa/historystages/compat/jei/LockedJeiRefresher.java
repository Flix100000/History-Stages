package net.bananemdnsa.historystages.compat.jei;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.world.item.ItemStack;

/**
 * Stateful applier that holds the current "hidden in JEI" set and applies diffs
 * against a {@link JeiOps} sink. The sink is abstracted so the logic stays
 * decoupled from JEI's runtime types.
 *
 * <p>Two things here are what used to make items stay hidden or come back wrong after a stage
 * change, which is why the recipe resend (and the full JEI restart it causes) had to cover for it:
 * <ul>
 *   <li>JEI's item list no longer contains what we removed from it. Asking only that list which
 *   items are locked can never find a hidden item again, so every hidden item looked unlocked.
 *   The candidates are therefore what JEI still lists <em>plus</em> what we took out.</li>
 *   <li>{@link ItemStack} has no equals, and JEI hands out a different instance from the one we
 *   removed. Two sets of stacks compared by identity never agree, so the diff was always
 *   "everything changed". Stacks are keyed by item and components instead.</li>
 * </ul>
 *
 * <p>Single-threaded usage assumed: all entry points run on the client render thread.
 * Methods are {@code synchronized} as defensive belt-and-suspenders since stage
 * sync packets are decoded on the network thread and re-posted via {@code mc.execute}.
 */
public final class LockedJeiRefresher {

    /** Minimal abstraction over the JEI runtime calls we use. */
    public interface JeiOps {
        void removeItems(Set<ItemStack> items);
        void addItems(Set<ItemStack> items);
    }

    /** A stack as JEI tells two stacks apart: same item, same components. */
    private record Key(ItemStack stack) {
        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && ItemStack.isSameItemSameComponents(stack, key.stack);
        }

        @Override
        public int hashCode() {
            return ItemStack.hashItemAndComponents(stack);
        }
    }

    private final JeiOps ops;
    /** The stacks we removed from JEI, kept so they can be asked about and put back. */
    private Map<Key, ItemStack> hidden = Map.of();

    public LockedJeiRefresher(JeiOps ops) {
        this.ops = ops;
    }

    /** Initial application: hides everything JEI lists that is locked, in one call. */
    public synchronized void applyInitial(boolean hideItemsEnabled, Collection<ItemStack> listedNow,
                                          Predicate<ItemStack> isLocked) {
        hidden = Map.of();
        applyDiff(hideItemsEnabled, listedNow, isLocked);
    }

    /**
     * Works out what should be hidden now — from what JEI lists and what we already took out —
     * and only adds or removes the difference. Nothing changed, no JEI call.
     */
    public synchronized void applyDiff(boolean hideItemsEnabled, Collection<ItemStack> listedNow,
                                       Predicate<ItemStack> isLocked) {
        Map<Key, ItemStack> candidates = new LinkedHashMap<>();
        for (ItemStack stack : listedNow) {
            if (stack != null && !stack.isEmpty()) candidates.putIfAbsent(new Key(stack), stack);
        }
        // Ours first in the sense that matters: a stack JEI no longer lists must still be asked.
        hidden.forEach(candidates::putIfAbsent);

        Map<Key, ItemStack> nowLocked = new LinkedHashMap<>();
        if (hideItemsEnabled) {
            candidates.forEach((key, stack) -> {
                if (isLocked.test(stack)) nowLocked.put(key, stack);
            });
        }

        Set<ItemStack> toShow = new java.util.HashSet<>();
        hidden.forEach((key, stack) -> {
            if (!nowLocked.containsKey(key)) toShow.add(stack);
        });
        Set<ItemStack> toHide = new java.util.HashSet<>();
        nowLocked.forEach((key, stack) -> {
            if (!hidden.containsKey(key)) toHide.add(stack);
        });

        if (!toShow.isEmpty()) ops.addItems(toShow);
        if (!toHide.isEmpty()) ops.removeItems(toHide);

        hidden = nowLocked;
    }

    /** Snapshot of the currently-hidden set (defensive copy). */
    public synchronized Set<ItemStack> currentlyHiddenItems() {
        return Set.copyOf(hidden.values());
    }
}
