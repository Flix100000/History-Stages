package net.bananemdnsa.historystages.data.lock;

import java.util.ArrayList;
import java.util.List;

/**
 * Slots one stage keeps shut in one place.
 *
 * <p>{@code target} is {@value #PLAYER} for the player's own inventory (slots are inventory
 * indices: 0-8 hotbar, 9-35 main, 36-39 armor, 40 offhand), a block id for containers opened from
 * that block, or a menu type id for menus that have no block behind them. Container slots are
 * menu slot numbers.
 */
public class SlotLockEntry {

    public static final String PLAYER = "player";

    private String target;
    private List<Integer> slots;
    private SlotLockMode mode;
    /** Null when it is the default look, so plain entries stay short on disk. */
    private SlotLook look;
    /** Shown on hover over the locked slot. Null when unset. */
    private String hint;

    public SlotLockEntry() {
        this(PLAYER, new ArrayList<>(), SlotLockMode.OVERLAY);
    }

    public SlotLockEntry(String target, List<Integer> slots, SlotLockMode mode) {
        this.target = target;
        this.slots = slots != null ? new ArrayList<>(slots) : new ArrayList<>();
        this.mode = mode;
    }

    public String target() {
        return target != null ? target : PLAYER;
    }

    /** Live list; editors mutate it in place. Never null. */
    public List<Integer> slots() {
        if (slots == null) slots = new ArrayList<>();
        return slots;
    }

    /** Overlay when unset: the gentler mode is the safer guess for a hand-written file. */
    public SlotLockMode mode() {
        return mode != null ? mode : SlotLockMode.OVERLAY;
    }

    public void setTarget(String target) { this.target = target; }
    public void setSlots(List<Integer> slots) { this.slots = slots != null ? new ArrayList<>(slots) : new ArrayList<>(); }
    public void setMode(SlotLockMode mode) { this.mode = mode; }

    /** Never null; an entry without a look block gets a fresh default one, not stored. */
    public SlotLook look() {
        return look != null ? look : new SlotLook();
    }

    public void setLook(SlotLook look) {
        this.look = look == null || look.isDefault() ? null : look;
    }

    public String hint() {
        return hint != null ? hint : "";
    }

    public void setHint(String hint) {
        this.hint = hint == null || hint.isBlank() ? null : hint;
    }

    public boolean isPlayerTarget() {
        return PLAYER.equals(target());
    }

    /** One engine key per slot, so the lock engine can narrow a slot query to the stages naming it. */
    public List<String> indexKeys() {
        List<String> keys = new ArrayList<>(slots().size());
        for (int slot : slots()) keys.add(key(target(), slot));
        return keys;
    }

    public static String key(String target, int slot) {
        return target + "#" + slot;
    }

    public SlotLockEntry copy() {
        SlotLockEntry copy = new SlotLockEntry(target(), slots(), mode);
        copy.look = look != null ? look.copy() : null;
        copy.hint = hint;
        return copy;
    }
}
