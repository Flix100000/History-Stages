package net.bananemdnsa.historystages.compat;

import java.util.Collection;

/**
 * Recipe viewer categories the recipe lock leaves alone: no dark overlay, no blocked hover, and
 * nothing hidden. JEI and EMI both ask this one rule, so a page can never be darkened in one
 * viewer and spared in the other.
 *
 * <p>JER is always on the list. Its pages only show where things come from, and darkening them
 * because a drop is locked hides exactly what the player needs to work towards the stage. Packs
 * add their own through {@code jei_hiding.lockOverlayExemptions}, for the same kind of page from
 * other mods.
 *
 * <p>Kept free of Minecraft and of the config so the matching can be pinned by a plain unit test.
 */
public final class ViewerCategoryExemptions {

    private static final String JER_NAMESPACE = "jeresources";

    private ViewerCategoryExemptions() {}

    /**
     * Whether a category is exempt. An entry with a colon names one category
     * ({@code create:mixing}); one without names every category of that mod ({@code create}).
     */
    public static boolean isExempt(String categoryId, Collection<? extends String> configured) {
        if (categoryId == null || categoryId.isEmpty()) return false;
        int colon = categoryId.indexOf(':');
        String namespace = colon >= 0 ? categoryId.substring(0, colon) : categoryId;
        if (JER_NAMESPACE.equals(namespace)) return true;
        if (configured == null) return false;
        for (String raw : configured) {
            if (raw == null) continue;
            String entry = raw.trim();
            if (entry.isEmpty()) continue;
            if (entry.indexOf(':') >= 0 ? entry.equals(categoryId) : entry.equals(namespace)) return true;
        }
        return false;
    }
}
