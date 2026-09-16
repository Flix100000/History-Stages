package net.bananemdnsa.historystages.events;

import java.util.UUID;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.util.lock.RecipeCraftContext;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;

/**
 * The single place that judges whether a recipe is locked.
 *
 * <p>Both questions below ask the same thing first: is anyone known to be crafting? A station that
 * knows sets a {@link RecipeCraftContext} around its one resolution and gets an answer across both
 * scopes; everything else — furnaces, hoppers, autocrafters, mod menus we do not hook — gets the
 * global-only answer it has always got.
 *
 * <p>Client and server move together on purpose. Were only one side to consult individual stages,
 * the player would see a preview they cannot take out, or an output the server refuses — worse
 * than either side being wrong on its own.
 */
public class RecipeHandler {
    /**
     * Checks if a recipe's output is locked based on history stages.
     * Uses ClientStageCache on the client side, SERVER_CACHE on the server side.
     */
    public static boolean isOutputLocked(Recipe<?> recipe, boolean isClientSide) {
        if (recipe == null) return false;

        ItemStack result;
        try {
            result = recipe.getResultItem(RegistryAccess.EMPTY);
        } catch (Exception e) {
            return false;
        }

        if (result.isEmpty()) return false;

        UUID crafter = crafter();

        // Respects lock_actions["recipe"]: if "recipe" is explicitly allowed, the recipe stays visible/craftable
        if (isClientSide) {
            return StageLockHelper.isActionLockedForClient(result, "recipe")
                    || (crafter != null
                            && StageLockHelper.isActionLockedByIndividualStageClient(result, "recipe"));
        }
        return StageLockHelper.isActionLockedForServer(result, "recipe")
                || (crafter != null
                        && StageLockHelper.isActionLockedByIndividualStage(result, crafter, "recipe"));
    }

    /** Overload without side info — defaults to server-side check. */
    public static boolean isOutputLocked(Recipe<?> recipe) {
        return isOutputLocked(recipe, false);
    }

    /**
     * Whether this recipe is gated for everyone on the server, by either route: its own id on a
     * stage, or an item it produces whose lock covers {@code recipe}.
     *
     * <p>Deliberately blind to who is asking. This is the answer given to a station that asked for
     * the whole recipe list rather than for one recipe, and that answer is cached and handed to
     * every station after it — so a per-player verdict here would let whoever walked past first
     * decide for everybody. Individual stages keep to the paths where a player is actually named.
     */
    public static boolean isLockedForEveryone(Recipe<?> recipe) {
        if (recipe == null) return false;

        ItemStack result;
        try {
            result = recipe.getResultItem(RegistryAccess.EMPTY);
        } catch (Exception e) {
            result = ItemStack.EMPTY;
        }
        if (!result.isEmpty() && StageLockHelper.isActionLockedForServer(result, "recipe")) return true;

        return StageLockHelper.isRecipeLockedForServer(recipe.getId().toString());
    }

    public static boolean isRecipeIdLocked(ResourceLocation recipeId, boolean isClientSide) {
        if (recipeId == null) return false;

        String id = recipeId.toString();
        UUID crafter = crafter();

        if (isClientSide) {
            // The client's crafter is always the local player, so the individual set to consult is
            // the one ClientStageStates already holds. The context only decides whether to ask.
            return crafter != null
                    ? StageLockHelper.isRecipeLockedForClient(id)
                    : StageLockHelper.isRecipeLockedForClientGlobalOnly(id);
        }
        return crafter != null
                ? StageLockHelper.isRecipeLockedForPlayer(id, crafter)
                : StageLockHelper.isRecipeLockedForServer(id);
    }

    public static boolean isRecipeIdLocked(ResourceLocation recipeId) {
        return isRecipeIdLocked(recipeId, false);
    }

    /**
     * The crafter of the resolution in progress, or null when there is none — which is also the
     * answer while {@code individualLockRecipes} is off. Reading the switch here rather than at
     * every station means turning it off restores the previous behaviour everywhere at once,
     * including on the client.
     */
    @Nullable
    private static UUID crafter() {
        if (!Config.GAMEPLAY.individualLockRecipes.get()) return null;
        return RecipeCraftContext.crafter();
    }
}
