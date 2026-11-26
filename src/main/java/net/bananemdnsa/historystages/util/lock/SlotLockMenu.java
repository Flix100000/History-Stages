package net.bananemdnsa.historystages.util.lock;

import org.jetbrains.annotations.Nullable;

/**
 * Mixed into every menu: which slot-lock target its container part answers to.
 *
 * <p>The server sets it when the menu opens (it knows the block); otherwise it is worked out from
 * the slots on first ask, which is right for menus whose slots hold the block entity itself and
 * falls back to the menu type for everything else.
 */
public interface SlotLockMenu {

    /** A block id, a menu type id, or null for a menu with nothing but the player's inventory. */
    @Nullable String historystages$slotTarget();

    void historystages$setSlotTarget(String target);
}
