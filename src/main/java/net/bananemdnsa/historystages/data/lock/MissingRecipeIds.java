package net.bananemdnsa.historystages.data.lock;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The result of the last {@link RecipeIdAudit} run, so the editor can paint the offending rows
 * red instead of making the author find it in the log.
 *
 * <p>Filled on both sides without a packet: the client applies the recipes the server sends it,
 * and the same mixin runs there — so both sides compute the same answer from data they already
 * have. A packet for something both ends already know would be ballast.
 */
public final class MissingRecipeIds {

    private MissingRecipeIds() {}

    private static volatile Set<String> missing = Set.of();
    private static volatile List<RecipeIdAudit.MissingRecipe> entries = List.of();

    public static void set(List<RecipeIdAudit.MissingRecipe> entries) {
        MissingRecipeIds.entries = List.copyOf(entries);
        missing = entries.stream()
                .map(RecipeIdAudit.MissingRecipe::recipeId)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static boolean isMissing(String recipeId) {
        return missing.contains(recipeId);
    }

    /**
     * The whole result, for the debug report. It re-reads this on every registry pass rather than
     * being told once at recipe load: a /history reload clears the report but applies no recipes.
     */
    public static List<RecipeIdAudit.MissingRecipe> entries() {
        return entries;
    }

    /** Empty until the first recipe load; the editor cannot be open before that anyway. */
    public static Set<String> all() {
        return missing;
    }
}
