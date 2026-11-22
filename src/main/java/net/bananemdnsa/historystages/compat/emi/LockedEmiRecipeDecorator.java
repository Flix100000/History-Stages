package net.bananemdnsa.historystages.compat.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeDecorator;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * Draws a "locked" overlay on EMI recipes whose output items or recipe IDs are stage-locked.
 * Registered globally for all EMI recipe categories.
 */
public class LockedEmiRecipeDecorator implements EmiRecipeDecorator {

    @Override
    public void decorateRecipe(EmiRecipe recipe, WidgetHolder widgets) {
        if (!isRecipeLocked(recipe)) return;

        int width = widgets.getWidth();
        int height = widgets.getHeight();

        // Semi-transparent dark overlay: z=200 sits above the recipe's item icons (~150) but below
        // tooltips (~400), so the "Locked" tooltip renders on top of the overlay rather than under it.
        widgets.addDrawable(0, 0, width, height, (guiGraphics, mouseX, mouseY, delta) -> {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 200);

            guiGraphics.fill(0, 0, width, height, 0xBB000000);

            Font font = Minecraft.getInstance().font;
            Component text = Component.translatable("jei.historystages.recipe_locked")
                    .withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD);
            int textWidth = font.width(text);
            guiGraphics.drawString(font, text, (width - textWidth) / 2, height / 2 - 4, 0xFFFFFF, true);

            guiGraphics.pose().popPose();
        }).tooltipText(lockedTooltipLines());
    }

    /** The tooltip shown when hovering a locked recipe. Shared with the RecipeScreen/SlotWidget mixins. */
    public static java.util.List<Component> lockedTooltipLines() {
        return java.util.List.of(
                Component.translatable("tooltip.historystages.emi.stage_locked"),
                Component.translatable("tooltip.historystages.emi.line1"),
                Component.translatable("tooltip.historystages.emi.line2")
        );
    }

    /**
     * The overlay's question, asked by the decorator and both hover mixins. Answers no while the
     * overlay is switched off, so the hover stays usable whenever nothing is drawn over the page.
     * Hiding goes through the other overload and keeps its own switch.
     */
    public static boolean isRecipeLocked(EmiRecipe recipe) {
        if (!net.bananemdnsa.historystages.Config.VISUAL.showLockOverlay.get()) return false;
        return isRecipeLocked(recipe, stack -> StageLockHelper.isActionLockedForClient(stack, "recipe")
                || StageLockHelper.isActionLockedByIndividualStageClient(stack, "recipe"));
    }

    /**
     * Same question with the output check swapped out. The recipe hiding passes one that follows
     * {@code lockedItemMultiStagePolicy}; the overlay has always been strict.
     */
    public static boolean isRecipeLocked(EmiRecipe recipe, Predicate<ItemStack> outputLocked) {
        // JER pages come through EMI's JEI bridge under JER's own category ids, so one rule
        // covers both viewers; see ViewerCategoryExemptions.
        if (recipe.getCategory() != null
                && net.bananemdnsa.historystages.compat.ViewerCategoryExemptions.isExempt(
                        recipe.getCategory().getId().toString(),
                        net.bananemdnsa.historystages.Config.VISUAL.lockOverlayExemptions.get())) {
            return false;
        }

        // 1. Check by recipe ID
        ResourceLocation recipeId = recipe.getId();
        if (recipeId != null
                && (StageLockHelper.isRecipeLockedForClient(recipeId.toString())
                    || net.bananemdnsa.historystages.events.RecipeHandler
                            .isFluidGatedForViewer(recipeId.toString()))) {
            return true;
        }

        // 2. Check by output items
        for (EmiStack output : recipe.getOutputs()) {
            ItemStack stack = output.getItemStack();
            if (!stack.isEmpty() && outputLocked.test(stack)) {
                return true;
            }
        }

        return false;
    }
}
