package net.bananemdnsa.historystages.util.lock;

/** What a slot is for one viewer right now. */
public enum SlotLockState {
    OPEN,
    HIDDEN,
    OVERLAY;

    /** Whether items may not move into or out of the slot. */
    public boolean blocks() {
        return this != OPEN;
    }
}
