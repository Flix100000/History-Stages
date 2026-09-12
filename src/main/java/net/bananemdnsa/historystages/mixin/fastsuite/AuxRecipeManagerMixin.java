package net.bananemdnsa.historystages.mixin.fastsuite;

import java.util.List;
import java.util.Optional;

import net.bananemdnsa.historystages.events.RecipeHandler;
import net.bananemdnsa.historystages.util.lock.RecipeResolutionFilter;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Puts the recipe gate back on the two lookups FastSuite answers by itself.
 *
 * <p>FastSuite does not rewrite the recipe manager, it subclasses it: its own manager is swapped in
 * on both sides and replaces exactly two methods, "which of your recipes fits this" and "which of
 * them all fit". Both hand the question to a pre-sorted, partly parallel list of its own and only
 * fall back to {@code super} for recipe types with fewer than a hundred recipes — crafting is never
 * one of those. Our gate lives in the bodies it replaced, so on that manager it is simply not
 * there. Reported as issue #121.
 *
 * <p>The half of the report that hurts is not the lock going quiet, it is what partial enforcement
 * does. The crafting table fills its result slot through the replaced lookup and shows the gated
 * item; taking it out goes through "what is left over", which FastSuite does <em>not</em> replace,
 * so that one still meets the gate and hears that no recipe exists. Vanilla has a fixed rule for
 * that case — no recipe means nothing is consumed — and puts the ingredients straight back into
 * the grid. Crafting for free at a stack of one, and more coming out than went in above that.
 *
 * <p>Reading the whole list is unaffected: those methods are inherited untouched, so the machines
 * that search for themselves stay gated.
 *
 * <p>{@code @Pseudo} + a string target + {@code require = 0}, the same pattern as the JEI, EMI and
 * Better Combat hooks: silently absent without FastSuite, and silently absent again if FastSuite
 * ever reshapes these two methods, rather than taking the game down with it.
 */
@Pseudo
@Mixin(targets = "dev.shadowsoffire.fastsuite.AuxRecipeManager", remap = false)
public class AuxRecipeManagerMixin {

    /**
     * The answer a station acts on, and the one the crafting table fills its result slot from.
     *
     * <p>At {@code RETURN} rather than {@code HEAD} so it also covers the shortcut FastSuite takes
     * before consulting its list at all, where a {@code lastRecipe} that still matches is handed
     * straight back. The crafting table passes what the grid resolved to last tick, so a gate
     * applied later would be the one thing a player could hold open.
     */
    @Inject(method = "getRecipeFor("
                    + "Lnet/minecraft/world/item/crafting/RecipeType;"
                    + "Lnet/minecraft/world/item/crafting/RecipeInput;"
                    + "Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/world/item/crafting/RecipeHolder;"
                    + ")Ljava/util/Optional;",
            at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private <I extends RecipeInput, T extends Recipe<I>> void historystages$gateRecipeFor(
            RecipeType<T> type, I input, Level level, RecipeHolder<T> lastRecipe,
            CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir) {
        Optional<RecipeHolder<T>> resolved = cir.getReturnValue();
        if (resolved.isEmpty()) return;
        if (!RecipeHandler.isLockedForResolution(resolved.get(), level.isClientSide())) return;
        cir.setReturnValue(historystages$filter().historystages$firstUnlocked(type, input, level));
    }

    @Inject(method = "getRecipesFor("
                    + "Lnet/minecraft/world/item/crafting/RecipeType;"
                    + "Lnet/minecraft/world/item/crafting/RecipeInput;"
                    + "Lnet/minecraft/world/level/Level;"
                    + ")Ljava/util/List;",
            at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private <I extends RecipeInput, T extends Recipe<I>> void historystages$gateRecipesFor(
            RecipeType<T> type, I input, Level level,
            CallbackInfoReturnable<List<RecipeHolder<T>>> cir) {
        cir.setReturnValue(historystages$filter()
                .historystages$withoutLocked(cir.getReturnValue(), level.isClientSide()));
    }

    /**
     * This manager, asked as the gate rather than as a recipe manager.
     *
     * <p>FastSuite's manager inherits from the vanilla one, so it carries the mixin that owns both
     * answers — including the walk that finds the next recipe on the same ingredients when the
     * first one is gated. Nothing about the gate is decided here.
     */
    private RecipeResolutionFilter historystages$filter() {
        return (RecipeResolutionFilter) (Object) this;
    }
}
