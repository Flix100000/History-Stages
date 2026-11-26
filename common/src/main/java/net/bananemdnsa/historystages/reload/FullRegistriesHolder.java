package net.bananemdnsa.historystages.reload;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * The registry view each datapack reload was built with, reloadable layer included, filled by
 * {@code ReloadableServerResourcesMixin}.
 *
 * <p>Keyed by the reload's recipe manager because that is what the server hands out for its
 * current data on both loaders; the resources record itself is private on Fabric. Weak, so a
 * replaced reload's view goes with it.
 */
public final class FullRegistriesHolder {

    private static final Map<RecipeManager, RegistryAccess> BY_RECIPES = new WeakHashMap<>();

    private FullRegistriesHolder() {}

    public static synchronized void remember(RecipeManager recipes, RegistryAccess fullRegistries) {
        BY_RECIPES.put(recipes, fullRegistries);
    }

    /** The full view of the server's current data, or {@code registryAccess()} if none was kept. */
    public static synchronized RegistryAccess of(MinecraftServer server) {
        RegistryAccess full = BY_RECIPES.get(server.getRecipeManager());
        return full != null ? full : server.registryAccess();
    }
}
