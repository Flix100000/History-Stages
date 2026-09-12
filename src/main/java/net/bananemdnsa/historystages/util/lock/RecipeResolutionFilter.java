package net.bananemdnsa.historystages.util.lock;

import java.util.List;
import java.util.Optional;

import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * The recipe gate, reachable from outside the recipe manager's own methods.
 *
 * <p>Both answers are worked out once, by the mixin sitting in the recipe manager — and every
 * recipe manager in the game is one, a mod's own subclass included. A mod that overrides the two
 * resolution methods walks straight past the gate inside them, so whatever puts the gate back has
 * to ask for the same two answers from the outside. Asking through here keeps the verdict and the
 * iteration order in one place rather than growing a second copy to drift away from it.
 *
 * <p>FastSuite is the case this exists for; see {@code mixin/fastsuite/AuxRecipeManagerMixin}.
 */
public interface RecipeResolutionFilter {

    /**
     * The first recipe of this type that matches {@code input} and is not gated, in the order the
     * vanilla manager walks.
     */
    <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> historystages$firstUnlocked(
            RecipeType<T> type, I input, Level level);

    /** {@code resolved} with every gated recipe taken out, or {@code resolved} itself if none is. */
    <T extends Recipe<?>> List<RecipeHolder<T>> historystages$withoutLocked(
            List<RecipeHolder<T>> resolved, boolean isClientSide);
}
