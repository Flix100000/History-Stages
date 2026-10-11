package net.bananemdnsa.historystages.client.display;

import net.bananemdnsa.historystages.data.lock.category.BuiltInLockMatching;
import net.bananemdnsa.historystages.client.cache.ClientIndividualStageCache;
import net.bananemdnsa.historystages.client.cache.ClientStageCache;
import net.bananemdnsa.historystages.data.display.DisplayMode;
import net.bananemdnsa.historystages.data.display.HiddenDisplayConfig;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.NbtMatcher;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StagePaths;
import net.bananemdnsa.historystages.data.lock.engine.CategoryLockIndexes;
import net.bananemdnsa.historystages.data.display.TextOverrideHolder;
import net.bananemdnsa.historystages.data.lock.NamedLockEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Client-side resolver that decides how a locked item's name and tooltip should be
 * displayed, based on the {@link HiddenDisplayConfig} of the stages that lock it.
 *
 * <p>Global and individual stages are resolved against their respective client caches and
 * share a single scoring pool, so an item may take its display from either kind.</p>
 *
 * <p>The hidden-display mode is stage-wide; only the REPLACE text varies per item via
 * overrides. When an item is locked by several stages, name and tooltip are resolved
 * <b>independently</b>; for each axis the winner is the stage that matches the item most
 * specifically — explicit item entry (3) &gt; tag (2) &gt; mod (1). On a tie a global stage
 * beats an individual one, and within a scope the stage that sorts first by folder path and
 * id wins, the same order the overview shows. A per-item REPLACE override
 * only applies when that axis won via an explicit item entry.</p>
 *
 * <p>Lock hints are resolved independently of name and tooltip: any matching locked stage
 * that disables them wins, so a stage can suppress the hints while leaving the item's own
 * name and tooltip untouched.</p>
 */
public final class HiddenDisplayResolver {

    private HiddenDisplayResolver() {}

    private static final int SCORE_ITEM = 3;
    private static final int SCORE_TAG = 2;
    private static final int SCORE_MOD = 1;

    /** Resolved name/tooltip effects for a single item. */
    public record Resolved(DisplayMode nameMode, String nameText,
                           DisplayMode tooltipMode, String tooltipText,
                           boolean showLockHints) {

        public static final Resolved NONE =
                new Resolved(DisplayMode.OFF, "", DisplayMode.OFF, "", true);

        public boolean changesName() { return nameMode != DisplayMode.OFF; }
        public boolean changesTooltip() { return tooltipMode != DisplayMode.OFF; }
    }

    public static Resolved resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Resolved.NONE;
        ResourceLocation res = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (res == null) return Resolved.NONE;

        String itemId = res.toString();
        String modId = res.getNamespace();

        // This runs on every getHoverName, which the item panel of a recipe viewer calls for
        // every item it lists. Narrowing to the stages that mention this item at all is what
        // makes the overwhelmingly common answer - "no stage has ever heard of it" - cost a map
        // lookup instead of a walk over the whole stage tree.
        //
        // The index is built from exactly the three entry lists matched below (items, tags,
        // mods), so it cannot name fewer stages than the walk would have found.
        Item item = stack.getItem();
        Accumulator acc = new Accumulator();
        // Global stages run first, so on an equal match score the global stage keeps the win.
        collect(CategoryLockIndexes.globalCandidates(itemId, modId, item),
                StageManager.getStages(), false, ClientStageCache::isStageUnlocked,
                itemId, modId, stack, acc);
        collect(CategoryLockIndexes.individualCandidates(itemId, modId, item),
                StageManager.getIndividualStages(), true, ClientIndividualStageCache::isStageUnlocked,
                itemId, modId, stack, acc);

        if (acc.nameMode == DisplayMode.OFF && acc.tooltipMode == DisplayMode.OFF && acc.showLockHints) {
            return Resolved.NONE;
        }
        return new Resolved(acc.nameMode, acc.nameText, acc.tooltipMode, acc.tooltipText, acc.showLockHints);
    }

    /** Mutable carry for a resolve pass, so global and individual stages share one scoring pool. */
    private static final class Accumulator {
        int bestNameScore;
        int bestTooltipScore;
        String bestNameRank;
        String bestTooltipRank;
        DisplayMode nameMode = DisplayMode.OFF;
        String nameText = "";
        DisplayMode tooltipMode = DisplayMode.OFF;
        String tooltipText = "";
        boolean showLockHints = true;
    }

    private static void collect(Collection<String> candidateIds,
                                Map<String, StageEntry> stages,
                                boolean individual,
                                Predicate<String> isUnlocked,
                                String itemId, String modId, ItemStack stack,
                                Accumulator acc) {
        for (String stageId : candidateIds) {
            StageEntry stage = stages.get(stageId);
            if (stage == null) continue;
            HiddenDisplayConfig cfg = stage.getHiddenDisplay();
            if (cfg.isNoop()) continue;
            if (isUnlocked.test(stageId)) continue;

            TextOverrideHolder holder;
            int score;
            ItemEntry itemMatch = matchedItemEntry(stage, itemId, stack);
            if (itemMatch != null) {
                holder = itemMatch;
                score = SCORE_ITEM;
            } else {
                NamedLockEntry tagMatch = matchedTag(stage, stack);
                if (tagMatch != null) {
                    holder = tagMatch;
                    score = SCORE_TAG;
                } else {
                    NamedLockEntry modMatch = matchedMod(stage, itemId, modId, stack);
                    if (modMatch == null) continue;
                    holder = modMatch;
                    score = SCORE_MOD;
                }
            }

            if (!cfg.isShowLockHints()) acc.showLockHints = false;

            // The candidates arrive in hash-map order, so a tie can't go to "whoever came first"
            // or the winner would change between reloads. It goes to the stage that sorts first
            // in the overview instead, and the scope prefix keeps a global stage ahead of any
            // individual one.
            String rank = (individual ? "1:" : "0:")
                    + StagePaths.join(StageManager.getStageFolder(stageId, individual), stageId);
            if (cfg.getNameMode() != DisplayMode.OFF
                    && beats(score, rank, acc.bestNameScore, acc.bestNameRank)) {
                acc.bestNameScore = score;
                acc.bestNameRank = rank;
                acc.nameMode = cfg.getNameMode();
                acc.nameText = resolveText(cfg.getNameText(), holder.getNameTextOverride());
            }
            if (cfg.getTooltipMode() != DisplayMode.OFF
                    && beats(score, rank, acc.bestTooltipScore, acc.bestTooltipRank)) {
                acc.bestTooltipScore = score;
                acc.bestTooltipRank = rank;
                acc.tooltipMode = cfg.getTooltipMode();
                acc.tooltipText = resolveText(cfg.getTooltipText(), holder.getTooltipTextOverride());
            }
        }
    }

    /** Higher score wins; on equal score the lower rank does. A null best rank means no winner yet. */
    private static boolean beats(int score, String rank, int bestScore, String bestRank) {
        if (score != bestScore) return score > bestScore;
        return bestRank == null || rank.compareTo(bestRank) < 0;
    }

    /** Override wins over the stage default; either may be empty. */
    private static String resolveText(String stageDefault, String override) {
        if (override != null) return override;
        return stageDefault != null ? stageDefault : "";
    }

    /** Returns the explicit item entry matching this stack (carrying overrides), or null. */
    private static ItemEntry matchedItemEntry(StageEntry stage, String itemId, ItemStack stack) {
        for (ItemEntry ie : stage.getItemEntries()) {
            if (!ie.getId().equals(itemId)) continue;
            if (!ie.hasNbt() || NbtMatcher.matches(stack, ie.getNbt())) return ie;
        }
        return null;
    }

    private static NamedLockEntry matchedTag(StageEntry stage, ItemStack stack) {
        Item item = stack.getItem();
        for (NamedLockEntry tagEntry : stage.getTagEntries()) {
            if (BuiltInLockMatching.tagEntryMatches(tagEntry, stack, item)) return tagEntry;
        }
        return null;
    }

    private static NamedLockEntry matchedMod(StageEntry stage, String itemId, String modId, ItemStack stack) {
        if (stage.isModExcepted(itemId, stack)) return null;
        for (NamedLockEntry modEntry : stage.getModEntries()) {
            if (modEntry.getId().equals(modId)) return modEntry;
        }
        return null;
    }
}
