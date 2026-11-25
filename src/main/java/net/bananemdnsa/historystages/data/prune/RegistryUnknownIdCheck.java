package net.bananemdnsa.historystages.data.prune;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Asks the game's registries. The server uses its own, the editor the client's, which is what the
 * player sees in the pickers.
 */
public final class RegistryUnknownIdCheck implements UnknownIdCheck {

    private final RegistryAccess access;
    private final RecipeManager recipes;

    public RegistryUnknownIdCheck(RegistryAccess access, RecipeManager recipes) {
        this.access = access;
        this.recipes = recipes;
    }

    @Override
    public boolean isUnknownItem(String id) {
        return !BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(id));
    }

    @Override
    public boolean isUnknownMod(String modId) {
        return !FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isUnknownEntity(String id) {
        return !BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(id));
    }

    @Override
    public boolean isUnknownFluid(String id) {
        return !BuiltInRegistries.FLUID.containsKey(ResourceLocation.parse(id));
    }

    @Override
    public boolean isUnknownEnchantment(String id) {
        // Data-driven since 1.21, so a datapack can add one without being a mod.
        return access.registry(Registries.ENCHANTMENT)
                .map(r -> !r.containsKey(ResourceLocation.parse(id)))
                .orElse(false);
    }

    @Override
    public boolean isUnknownBlock(String id) {
        return !BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(id));
    }

    @Override
    public boolean isUnknownEffect(String id) {
        return !BuiltInRegistries.MOB_EFFECT.containsKey(ResourceLocation.parse(id));
    }

    /**
     * Only when the recipe is gone and its namespace is no loaded mod. A missing id alone is often
     * a script recipe whose id moved, and a datapack's namespace is not a mod at all.
     */
    @Override
    public boolean isUnknownRecipe(String id) {
        ResourceLocation rl = ResourceLocation.parse(id);
        if (recipes != null && recipes.byKey(rl).isPresent()) return false;
        return !FabricLoader.getInstance().isModLoaded(rl.getNamespace());
    }
}
