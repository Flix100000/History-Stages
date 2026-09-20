package net.bananemdnsa.historystages.client;

import net.minecraft.client.Minecraft;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class OptionalRecipeViewHooks {
    private OptionalRecipeViewHooks() {
    }

    public static void refreshAll() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        if (!minecraft.isSameThread()) {
            minecraft.execute(OptionalRecipeViewHooks::refreshAll);
            return;
        }

        RecipeViewerVisibility.invalidateCache();
    }
}
