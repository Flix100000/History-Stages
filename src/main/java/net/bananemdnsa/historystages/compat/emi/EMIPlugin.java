package net.bananemdnsa.historystages.compat.emi;

import com.mojang.logging.LogUtils;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.compat.ScrollVariants;
import net.bananemdnsa.historystages.compat.StageDisplayPath;
import net.bananemdnsa.historystages.init.ModBlocks;
import net.bananemdnsa.historystages.init.ModItems;
import net.bananemdnsa.historystages.research.BoosterUtil;
import net.bananemdnsa.historystages.research.ResearchBoosterRegistry;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Predicate;

@EmiEntrypoint
public class EMIPlugin implements EmiPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void register(EmiRegistry registry) {
        // Register the locked recipe decorator globally for all categories
        registry.addRecipeDecorator(new LockedEmiRecipeDecorator());

        // Treat scrolls with different StageResearch as distinct EMI entries (parity with JEI subtypes)
        Comparison stageComparison = Comparison.compareData(
                stack -> ScrollVariants.readStageResearch(stack.getItemStack()));
        registry.setDefaultComparison(ModItems.RESEARCH_SCROLL.get(), stageComparison);
        registry.setDefaultComparison(ModItems.CREATIVE_SCROLL.get(), stageComparison);

        registry.setDefaultComparison(ModItems.RESEARCH_SCROLL_OPEN.get(), stageComparison);

        // Add one scroll variant per stage so they appear in EMI
        for (ItemStack scroll : ScrollVariants.buildAllStageScrolls()) {
            registry.addEmiStack(EmiStack.of(scroll));
        }

        // The resealing recipe is a special recipe with no declared ingredients, so no viewer can
        // derive it — one display entry per stage is added by hand. EMI can show the open scroll
        // as a remainder, which is exactly what happens: only the paper is spent.
        if (Config.GAMEPLAY.enableScrollResealing.get()) {
            for (String stageId : ScrollVariants.scrollableStageIds()) {
                EmiStack open = EmiStack.of(ScrollVariants.createOpenScroll(stageId));
                registry.addEmiStack(open);
                registry.addRecipe(new EmiCraftingRecipe(
                        List.of(open.copy().setRemainder(open.copy()), EmiStack.of(Items.PAPER)),
                        EmiStack.of(ScrollVariants.createScroll(stageId)),
                        ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID,
                                "reseal_scroll/" + StageDisplayPath.of(stageId))));
            }
        }

        BoosterEmiCategory category = new BoosterEmiCategory();
        registry.addCategory(category);

        ResearchBoosterRegistry.forEachStack((stack, booster) -> {
            registry.addRecipe(new BoosterEmiRecipe(
                    category, stack,
                    BoosterUtil.percent(booster.speedReduction()),
                    BoosterUtil.percent(booster.costReduction())));
            registry.addWorkstation(category, EmiStack.of(stack));
        });

        // Pedestal opens the category too (parity with JEI registerRecipeCatalysts)
        registry.addWorkstation(category, EmiStack.of(new ItemStack(ModBlocks.RESEARCH_PEDESTAL.get())));

        // Fortunately, as long as actually reload EMI, we don't have to calculate diffs and can always recalculate
        // everything from scratch! Actually, EMI errors if we try to manually re-insert the diffs.
        try {
            boolean hideItems = Config.VISUAL.hideLockedItemsInJei.get();
            boolean hideRecipes = Config.VISUAL.hideLockedRecipesInJei.get();
            applyItemHiding(hideItems, registry);
            applyRecipeHiding(hideRecipes, registry);
            LOGGER.info("[HistoryStages/EMI] Initial hide pass complete (items={}, recipes={}).", hideItems, hideRecipes);
        } catch (Exception e) {
            LOGGER.warn("[HistoryStages/EMI] Initial hide pass failed", e);
        }
    }

    private static boolean isItemLocked(EmiStack emiStack) {
        Predicate<ItemStack> isLocked = switch (Config.VISUAL.lockedItemMultiStagePolicy.get()) {
            case STRICT  -> stack -> StageLockHelper.isItemActionLockedForClient(stack, "recipe");
            case LENIENT -> stack -> StageLockHelper.isItemActionLockedForClientLenient(stack, "recipe");
        };
        return isLocked.test(emiStack.getItemStack());
    }

    /**
     * Hides/un-hides locked items.
     */
    public static synchronized void applyItemHiding(boolean enabled, EmiRegistry registry) {
        if (!enabled) return;
        registry.removeEmiStacks(EMIPlugin::isItemLocked);
    }

    /**
     * Hides/un-hides recipes whose outputs contain a locked item.
     */
    private static synchronized void applyRecipeHiding(boolean enabled, EmiRegistry registry) {
        if (!enabled) return;
        registry.removeRecipes(recipe -> {
            for (EmiStack stack : recipe.getOutputs()) {
                if (isItemLocked(stack)) {
                    return true;
                }
            }
            return false;
        });
    }
}
