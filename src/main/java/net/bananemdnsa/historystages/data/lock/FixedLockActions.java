package net.bananemdnsa.historystages.data.lock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lock actions a stage fixes for every entry of a kind. Each fixed action carries its value —
 * {@code true} locked, {@code false} allowed — and that value wins over whatever the entry says.
 * Actions that are not fixed stay the entry's own business.
 *
 * <p>One map for items, tags and mods — they share {@code LockActions.ITEM} — one for fluids,
 * and one for the interaction actions of interaction-locked mobs. {@code null} and an empty map
 * both fix nothing; the constructor turns empty into null so a stage that never used this writes
 * no key.
 */
public final class FixedLockActions {

    private Map<String, Boolean> items;
    private Map<String, Boolean> fluids;
    private Map<String, Boolean> interactions;

    public FixedLockActions() {}

    public FixedLockActions(Map<String, Boolean> items, Map<String, Boolean> fluids,
                            Map<String, Boolean> interactions) {
        this.items = copyOrNull(items);
        this.fluids = copyOrNull(fluids);
        this.interactions = copyOrNull(interactions);
    }

    /** Fixed actions for items, tags and mods with their value, or null when none are. */
    public Map<String, Boolean> getItems() { return items; }

    /** Fixed actions for fluids with their value, or null when none are. */
    public Map<String, Boolean> getFluids() { return fluids; }

    /** Fixed interaction actions with their value, or null when none are. */
    public Map<String, Boolean> getInteractions() { return interactions; }

    public boolean isEmpty() { return items == null && fluids == null && interactions == null; }

    public FixedLockActions copy() {
        return new FixedLockActions(items, fluids, interactions);
    }

    /**
     * The locked actions of an entry once the fixed ones are applied.
     *
     * @param entryActions the entry's own list; null means every action of {@code vocabulary}
     * @param fixed        fixed actions and their values; null for none
     * @return null when nothing is fixed and the entry has no list, which still means every action
     */
    public static List<String> apply(List<String> entryActions, Map<String, Boolean> fixed,
                                     List<String> vocabulary) {
        if (fixed == null || fixed.isEmpty()) return entryActions;
        List<String> locked = new ArrayList<>(entryActions != null ? entryActions : vocabulary);
        for (Map.Entry<String, Boolean> e : fixed.entrySet()) {
            if (Boolean.TRUE.equals(e.getValue())) {
                if (!locked.contains(e.getKey())) locked.add(e.getKey());
            } else {
                locked.remove(e.getKey());
            }
        }
        return locked;
    }

    private static Map<String, Boolean> copyOrNull(Map<String, Boolean> map) {
        return map != null && !map.isEmpty() ? new LinkedHashMap<>(map) : null;
    }
}
