package net.bananemdnsa.historystages.compat.jei;

import mezz.jei.api.recipe.category.IRecipeCategory;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.compat.ViewerCategoryExemptions;

/** JEI's side of {@link ViewerCategoryExemptions}: JER plus whatever the pack lists. */
public final class ExemptCategories {

    private ExemptCategories() {}

    public static boolean isExempt(IRecipeCategory<?> category) {
        return category != null && isExempt(category.getRecipeType().getUid().toString());
    }

    /** By id, for the hiding pass, which walks recipe types rather than categories. */
    public static boolean isExempt(String categoryId) {
        return ViewerCategoryExemptions.isExempt(categoryId, Config.VISUAL.lockOverlayExemptions.get());
    }
}
