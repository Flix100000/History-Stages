package net.bananemdnsa.historystages.mixin;

import java.util.List;

import net.bananemdnsa.historystages.reload.FullRegistriesHolder;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the registry view a datapack reload was built with, reloadable layer included.
 *
 * <p>The targeted reload fires the tags event again, and mods read loot tables from what it hands
 * them. {@code MinecraftServer.registryAccess()} has no loot tables in 26.2, so it takes the view
 * the loaders themselves pass when the reload is real.
 */
@Mixin(ReloadableServerResources.class)
public class ReloadableServerResourcesMixin {

    @Shadow @Final private RecipeManager recipes;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void historystages$keepFullRegistries(LayeredRegistryAccess<RegistryLayer> registries,
                                                  HolderLookup.Provider lookup, FeatureFlagSet features,
                                                  Commands.CommandSelection selection, List<?> postponedTags,
                                                  PermissionSet permissions, List<?> newComponents,
                                                  CallbackInfo ci) {
        FullRegistriesHolder.remember(recipes, registries.compositeAccess());
    }
}
