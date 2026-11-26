package net.bananemdnsa.historystages.compat.jei;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.category.extensions.IRecipeCategoryDecorator;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

/**
 * Draws a "locked" overlay on recipes whose output items or recipe IDs are stage-locked.
 * Renders at a high z-level to appear above item icons.
 */
public class LockedRecipeDecorator<T> implements IRecipeCategoryDecorator<T> {

    @Override
    public void draw(T recipe, IRecipeCategory<T> category, IRecipeSlotsView recipeSlotsView,
                     GuiGraphicsExtractor guiGraphics, double mouseX, double mouseY) {
        if (!net.bananemdnsa.historystages.Config.VISUAL.showLockOverlay.get()
                || ExemptCategories.isExempt(category) || !isRecipeLocked(recipe, recipeSlotsView)) return;

        int width = category.getWidth();
        int height = category.getHeight();

        // GUI layers draw in submission order, so no z translate: the decorator comes after
        // the slots.
        // Semi-transparent dark overlay
        guiGraphics.fill(0, 0, width, height, 0xBB000000);

        // "Locked" text centered
        Font font = Minecraft.getInstance().font;
        net.minecraft.network.chat.Component text = net.minecraft.network.chat.Component
                .translatable("jei.historystages.recipe_locked")
                .withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD);
        int textWidth = font.width(text);
        guiGraphics.text(font, text, (width - textWidth) / 2, height / 2 - 4, 0xFFFFFFFF, true);
    }

    private boolean isRecipeLocked(T recipe, IRecipeSlotsView recipeSlotsView) {
        // 1. Check by recipe ID if it's a vanilla RecipeHolder
        if (recipe instanceof RecipeHolder<?> holder) {
            Identifier recipeId = holder.id().identifier();
            if (recipeId != null
                    && (StageLockHelper.isRecipeLockedForClient(recipeId.toString())
                        || net.bananemdnsa.historystages.events.RecipeHandler
                                .isFluidGatedForViewer(recipeId.toString()))) {
                return true;
            }
        }

        // 2. Check by output items (works for all recipe types including modded)
        List<IRecipeSlotView> outputSlots = recipeSlotsView.getSlotViews(RecipeIngredientRole.OUTPUT);
        for (IRecipeSlotView slot : outputSlots) {
            var displayed = slot.getDisplayedItemStack();
            if (displayed.isPresent()) {
                ItemStack stack = displayed.get();
                if (!stack.isEmpty()
                        && (StageLockHelper.isActionLockedForClient(stack, "recipe")
                            || StageLockHelper.isActionLockedByIndividualStageClient(stack, "recipe"))) {
                    return true;
                }
            }
        }

        return false;
    }
}
