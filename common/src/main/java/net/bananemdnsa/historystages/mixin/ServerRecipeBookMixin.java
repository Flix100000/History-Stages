package net.bananemdnsa.historystages.mixin;

import java.util.function.Consumer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.bananemdnsa.historystages.util.lock.RecipeBookGate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps locked recipes out of the vanilla recipe book (1.21.1 did this on the client, from the
 * recipe list every client had; 26.2 clients get only the entries their book holds, from here).
 *
 * <p>Only the display entries sent are filtered. The known set, which is saved with the player,
 * keeps every recipe, so an unlock brings them back with the next full send
 * ({@link RecipeBookGate#resend}).
 */
@Mixin(ServerRecipeBook.class)
public class ServerRecipeBookMixin {

    private static final String DISPLAYS =
            "Lnet/minecraft/stats/ServerRecipeBook$DisplayResolver;displaysForRecipe(Lnet/minecraft/resources/ResourceKey;Ljava/util/function/Consumer;)V";

    @WrapOperation(method = {"sendInitialRecipeBook", "addRecipes"}, at = @At(value = "INVOKE", target = DISPLAYS))
    private void historystages$skipLocked(ServerRecipeBook.DisplayResolver resolver, ResourceKey<Recipe<?>> id,
                                          Consumer<RecipeDisplayEntry> output, Operation<Void> original,
                                          @Local(argsOnly = true) ServerPlayer player) {
        if (RecipeBookGate.hides(player, id)) return;
        original.call(resolver, id, output);
    }
}
