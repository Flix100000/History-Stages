package net.bananemdnsa.historystages.compat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Turns the exemption list into ticks and back, for the category picker.
 *
 * <p>The list holds two kinds of entry: a mod id ticks the whole mod, a category id ticks one
 * category. The picker only knows the categories installed right now, so an entry it cannot show
 * (a mod that is not in this instance, a category from a viewer that is not loaded) is carried
 * through untouched. Dropping it would quietly undo a pack setting just by opening the picker.
 *
 * <p>Free of Minecraft so the round trip can be pinned by a unit test.
 */
public final class ViewerCategorySelection {

    private final Set<String> wholeMods = new LinkedHashSet<>();
    private final Set<String> categories = new LinkedHashSet<>();

    public ViewerCategorySelection(Collection<? extends String> configured) {
        if (configured == null) return;
        for (String raw : configured) {
            if (raw == null) continue;
            String entry = raw.trim();
            if (entry.isEmpty()) continue;
            (entry.indexOf(':') >= 0 ? categories : wholeMods).add(entry);
        }
    }

    public boolean isWholeMod(String namespace) { return wholeMods.contains(namespace); }

    /** Ticked on its own or through its mod. */
    public boolean isTicked(String categoryId) {
        return categories.contains(categoryId) || wholeMods.contains(namespaceOf(categoryId));
    }

    public void setWholeMod(String namespace, boolean on) {
        if (on) wholeMods.add(namespace); else wholeMods.remove(namespace);
    }

    public void setCategory(String categoryId, boolean on) {
        if (on) categories.add(categoryId); else categories.remove(categoryId);
    }

    /** How many entries the list will hold. */
    public int size() {
        return toConfig().size();
    }

    /**
     * The list to store. Whole mods first, then single categories, each sorted so the file does
     * not reshuffle on every save. A category inside a ticked mod is left out: the mod entry
     * already covers it, and keeping both would leave a stale tick behind once the mod is unticked.
     */
    public List<String> toConfig() {
        List<String> out = new ArrayList<>(new TreeSet<>(wholeMods));
        for (String id : new TreeSet<>(categories)) {
            if (!wholeMods.contains(namespaceOf(id))) out.add(id);
        }
        return out;
    }

    public static String namespaceOf(String categoryId) {
        int colon = categoryId.indexOf(':');
        return colon >= 0 ? categoryId.substring(0, colon) : categoryId;
    }
}
