package net.bananemdnsa.historystages.client.editor.recipe;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Decides how a recipe uses an item, for the editor's "recipes using this item" popup.
 *
 * <p>Works on plain id sets, one per ingredient slot, so it can be tested without Minecraft. The
 * popup ticks {@link Kind#ONLY_LOCKED} recipes and leaves {@link Kind#WORKS_WITHOUT} ones
 * unticked: a slot that also takes something unlocked means the recipe can still be made without
 * the locked item, and locking it would take away more than the user asked for.
 */
public final class IngredientUsage {

    public enum Kind { NONE, ONLY_LOCKED, WORKS_WITHOUT }

    private IngredientUsage() {
    }

    /** How one slot relates to the clicked item. */
    public static Kind slotKind(Collection<String> slot, String clicked, Set<String> locked) {
        if (!slot.contains(clicked)) return Kind.NONE;
        return locked.containsAll(slot) ? Kind.ONLY_LOCKED : Kind.WORKS_WITHOUT;
    }

    /**
     * One exclusive slot is enough: if any slot that takes the clicked item takes nothing but
     * locked items, the recipe cannot be made without locked material.
     */
    public static Kind classify(List<? extends Collection<String>> slots, String clicked,
                                Set<String> locked) {
        boolean found = false;
        for (Collection<String> slot : slots) {
            Kind kind = slotKind(slot, clicked, locked);
            if (kind == Kind.ONLY_LOCKED) return Kind.ONLY_LOCKED;
            if (kind == Kind.WORKS_WITHOUT) found = true;
        }
        return found ? Kind.WORKS_WITHOUT : Kind.NONE;
    }

    /**
     * Whether the recipe needs locked material at all, whichever locked item that is. Item mode
     * asks this of every recipe making a result, including those that never mention the clicked
     * item.
     */
    public static boolean requiresLocked(List<? extends Collection<String>> slots, Set<String> locked) {
        for (Collection<String> slot : slots) {
            if (!slot.isEmpty() && locked.containsAll(slot)) return true;
        }
        return false;
    }

    /** An item only counts as locked-only when no recipe makes it without locked material. */
    public static Kind forResult(List<Boolean> requiresLockedPerRecipe) {
        if (requiresLockedPerRecipe.isEmpty()) return Kind.NONE;
        return requiresLockedPerRecipe.contains(Boolean.FALSE) ? Kind.WORKS_WITHOUT : Kind.ONLY_LOCKED;
    }
}
