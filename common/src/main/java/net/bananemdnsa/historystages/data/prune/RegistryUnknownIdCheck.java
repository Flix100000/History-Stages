package net.bananemdnsa.historystages.data.prune;

import net.bananemdnsa.historystages.platform.Services;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;

/**
 * Asks the game's registries. The server uses its own, the editor the client's, which is what the
 * player sees in the pickers.
 */
public final class RegistryUnknownIdCheck implements UnknownIdCheck {

    private final RegistryAccess access;
    private final java.util.Set<Identifier> recipeIds;

    /**
     * {@code recipeIds}: the recipes this side knows, or null when it knows none. The 26.2 client
     * holds no recipe manager, only the list the editor fetched, so the check takes ids.
     */
    public RegistryUnknownIdCheck(RegistryAccess access, @org.jetbrains.annotations.Nullable java.util.Set<Identifier> recipeIds) {
        this.access = access;
        this.recipeIds = recipeIds;
    }

    /** Every recipe id of a recipe collection; null for an empty one, which says nothing. */
    public static @org.jetbrains.annotations.Nullable java.util.Set<Identifier> idsOf(
            java.util.Collection<? extends net.minecraft.world.item.crafting.RecipeHolder<?>> recipes) {
        if (recipes.isEmpty()) return null;
        java.util.Set<Identifier> ids = new java.util.HashSet<>(recipes.size() * 2);
        for (var holder : recipes) ids.add(holder.id().identifier());
        return ids;
    }

    @Override
    public boolean isUnknownItem(String id) {
        return !BuiltInRegistries.ITEM.containsKey(Identifier.parse(id));
    }

    @Override
    public boolean isUnknownMod(String modId) {
        return !Services.isModLoaded(modId);
    }

    @Override
    public boolean isUnknownEntity(String id) {
        return !BuiltInRegistries.ENTITY_TYPE.containsKey(Identifier.parse(id));
    }

    @Override
    public boolean isUnknownFluid(String id) {
        return !BuiltInRegistries.FLUID.containsKey(Identifier.parse(id));
    }

    @Override
    public boolean isUnknownEnchantment(String id) {
        // Data-driven since 1.21, so a datapack can add one without being a mod.
        return access.lookup(Registries.ENCHANTMENT)
                .map(r -> !r.containsKey(Identifier.parse(id)))
                .orElse(false);
    }

    @Override
    public boolean isUnknownSlotTarget(String id) {
        Identifier key = Identifier.parse(id);
        return !BuiltInRegistries.BLOCK.containsKey(key) && !BuiltInRegistries.MENU.containsKey(key);
    }

    @Override
    public boolean isUnknownBlock(String id) {
        return !BuiltInRegistries.BLOCK.containsKey(Identifier.parse(id));
    }

    @Override
    public boolean isUnknownEffect(String id) {
        return !BuiltInRegistries.MOB_EFFECT.containsKey(Identifier.parse(id));
    }

    /**
     * Only when the recipe is gone and its namespace is no loaded mod. A missing id alone is often
     * a script recipe whose id moved, and a datapack's namespace is not a mod at all.
     */
    @Override
    public boolean isUnknownRecipe(String id) {
        Identifier rl = Identifier.parse(id);
        if (recipeIds != null && recipeIds.contains(rl)) return false;
        return !Services.isModLoaded(rl.getNamespace());
    }
}
