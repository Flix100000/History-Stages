package net.bananemdnsa.historystages.client.editor.tab;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Turns the "Applies to" list's ticks into the spared item types an entry stores, and back.
 *
 * <p>Ticked means locked, because that is what a checkbox in a lock editor reads as. The entry
 * stores the opposite — the spared types — so a type a mod adds later is locked by default.
 */
public final class ItemTypeSelection {

    private ItemTypeSelection() {}

    /** The offered types that are not spared: what the list opens with ticked. */
    public static List<String> locked(List<String> all, List<String> spared) {
        List<String> locked = new ArrayList<>();
        for (String id : all) if (!spared.contains(id)) locked.add(id);
        return locked;
    }

    public static List<String> spared(List<String> all, Collection<String> locked) {
        return spared(all, locked, List.of());
    }

    /**
     * The offered types left unticked, plus any previously spared type that is no longer
     * offered. A mod being removed must not quietly turn its spared types back into locked ones
     * should it come back.
     */
    public static List<String> spared(List<String> all, Collection<String> locked, List<String> previouslySpared) {
        List<String> spared = new ArrayList<>();
        for (String id : all) if (!locked.contains(id)) spared.add(id);
        for (String id : previouslySpared) if (!all.contains(id) && !spared.contains(id)) spared.add(id);
        return spared;
    }
}
