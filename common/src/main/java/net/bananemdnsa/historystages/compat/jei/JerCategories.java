package net.bananemdnsa.historystages.compat.jei;

import mezz.jei.api.recipe.category.IRecipeCategory;

// JER pages (mob drops, dungeon loot, world gen, plants, villagers) only tell the player where
// things come from. Darkening them because a drop is locked hides exactly the info needed to
// work towards the stage, so the recipe lock never applies there.
public final class JerCategories {

    private static final String JER_NAMESPACE = "jeresources";

    private JerCategories() {}

    public static boolean isJer(IRecipeCategory<?> category) {
        return category != null
                && JER_NAMESPACE.equals(category.getRecipeType().getUid().getNamespace());
    }
}
