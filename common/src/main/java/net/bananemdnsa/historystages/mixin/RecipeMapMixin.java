package net.bananemdnsa.historystages.mixin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import net.bananemdnsa.historystages.data.lock.VisibleRecipes;
import net.bananemdnsa.historystages.events.RecipeHandler;
import net.bananemdnsa.historystages.platform.Services;
import net.bananemdnsa.historystages.util.lock.RecipeResolutionFilter;
import net.bananemdnsa.historystages.util.lock.ResolutionSide;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The two list lookups 1.21.1 had on the recipe manager ({@code getRecipesFor} and
 * {@code getAllRecipesFor}). 26.2 moved them to {@link RecipeMap}; the gating is the same as
 * before, see {@code RecipeManagerMixin} for which route is gated where.
 */
@Mixin(RecipeMap.class)
public class RecipeMapMixin {

    /** "Which recipes of this type fit": asking, gated on both sides like {@code getRecipeFor}. */
    @Inject(method = "getRecipesFor", at = @At("RETURN"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void historystages$filterGetRecipesFor(
            RecipeType<T> type, I container, Level level, CallbackInfoReturnable<Stream<RecipeHolder<T>>> cir) {
        boolean isClient = ResolutionSide.isClient(level);
        cir.setReturnValue(cir.getReturnValue()
                .filter(holder -> !RecipeHandler.isLockedForResolution(holder, isClient)));
    }

    /**
     * One type's whole list: reading, gated on the server's own map only (a list has no level to
     * say which side asks). Vanilla itself only reads it through {@code getRecipesFor} above.
     */
    @Inject(method = "byType", at = @At("RETURN"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void historystages$filterByType(
            RecipeType<T> type, CallbackInfoReturnable<Collection<RecipeHolder<T>>> cir) {
        MinecraftServer server = Services.currentServer();
        if (server == null) return;
        Object manager = server.getRecipeManager();
        if (((RecipeResolutionFilter) manager).historystages$recipeMap() != (Object) this) return;
        Collection<RecipeHolder<T>> loaded = cir.getReturnValue();
        List<RecipeHolder<T>> asList = loaded instanceof List<RecipeHolder<T>> list ? list : new ArrayList<>(loaded);
        cir.setReturnValue(VisibleRecipes.ofType(manager, type, asList));
    }
}
