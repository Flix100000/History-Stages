package net.bananemdnsa.historystages.api.lock;

/**
 * What a slot is for one player right now, as {@link SlotLockQuery} reports it.
 *
 * @since API 6.2
 */
public enum SlotState {
    /** Not locked; items move in and out as usual. */
    OPEN,
    /** Locked and gone: not drawn, not hoverable, refuses items. */
    HIDDEN,
    /** Locked but visible, with a lock drawn over it; refuses items. */
    OVERLAY;

    /** Whether items may not move into or out of the slot. */
    public boolean isLocked() {
        return this != OPEN;
    }
}
