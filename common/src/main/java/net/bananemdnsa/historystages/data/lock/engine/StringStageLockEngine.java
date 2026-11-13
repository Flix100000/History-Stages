package net.bananemdnsa.historystages.data.lock.engine;

import net.bananemdnsa.historystages.api.stage.StageStateView;

import net.bananemdnsa.historystages.api.stage.StageScope;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.lock.category.CategoryLockResolver;
import net.bananemdnsa.historystages.data.lock.category.LockCategories;
import net.bananemdnsa.historystages.api.lock.LockCategory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The engine the mod runs on: every lock question is answered by asking a
 * {@link net.bananemdnsa.historystages.api.lock.LockCategory} through
 * {@link CategoryLockResolver}, over stages read from {@link StageManager}.
 *
 * <p>Still string-based — a stage is an id and a lock check is a walk over entries. What Phase 8
 * changed is <em>where</em> that walk lives: in the categories, one implementation per kind of
 * thing, instead of a dozen near-identical global/individual method pairs on the store. The
 * representation is what Phase 10 replaces, and it replaces this class rather than reaching past
 * it.
 *
 * <p>This class holds no match logic of its own. Its job is to pick the category, build the
 * subject, and hand both to the resolver. Two exceptions, both argued where they stand:
 * {@link #isItemActionLocked}, which is a precedence question rather than a gating one, and
 * {@link #gatingStagesForEnchantment}, whose locks are a sub-view of item entries and not a
 * category.
 */
public class StringStageLockEngine implements StageLockEngine {

    /**
     * Items, mods and tags are three categories but one question: does this stage gate this item.
     * They are asked together, in one pass over the candidate stages, because asking them
     * separately would name a stage that gates by id <em>and</em> by mod twice, and in a
     * different order — and that order is what the "you still need" tooltip prints.
     */
    private static final List<String> ITEM_CATEGORY_IDS =
            List.of("historystages:items", "historystages:fluids",
                    "historystages:enchantments", "historystages:effects",
                    "historystages:mods", "historystages:tags");

    /**
     * What a stack carries that the item id cannot show, read once per question.
     *
     * <p>Decides whether the per-id memo may be used at all. Two stacks of one id can carry
     * different fluids or potions, so a stack carrying anything never reads the memo and never
     * writes it. Otherwise a water bottle asked first would hand its "free" to every potion after
     * it, and an empty modded tank to the full one.
     */
    private record StackFacts(@Nullable String fluidId, StackContents contents) {
        boolean memoUsable() { return fluidId == null && contents.isEmpty(); }
    }

    private static StackFacts factsOf(@Nullable ItemStack stack) {
        return new StackFacts(fluidOf(stack), contentsOf(stack));
    }

    @Override
    public List<String> gatingStagesForItem(String itemId, String modId,
                                            @Nullable ItemStack stack, StageScope scope) {
        StackFacts facts = factsOf(stack);
        if (facts.memoUsable()) {
            CategoryLockIndexes.ItemGating remembered =
                    CategoryLockIndexes.rememberedItemGating(itemId, scope);
            if (remembered != null) return remembered.stages();
        }
        return computeItemGating(itemId, modId, stack, scope, facts).stages();
    }

    /**
     * Whether this item is locked for this viewer, answered in bits where it can be.
     *
     * <p>The list-returning {@link #gatingStagesForItem} exists for callers that print the
     * missing stages. This one only has to say yes or no, which is what nearly every call
     * actually wants — and once the answer for an item is remembered, both sides of the question
     * are already masks and the comparison is a handful of AND operations rather than a lookup
     * per gating stage.
     */
    @Override
    public boolean isItemLocked(String itemId, String modId, @Nullable ItemStack stack,
                                StageScope scope, StageStateView state, @Nullable StageMask unlocked) {
        StackFacts facts = factsOf(stack);
        CategoryLockIndexes.ItemGating gating = facts.memoUsable()
                ? CategoryLockIndexes.rememberedItemGating(itemId, scope) : null;
        if (gating == null) gating = computeItemGating(itemId, modId, stack, scope, facts);

        if (unlocked != null && gating.hasBits()) {
            return gating.isLockedFor(unlocked);
        }
        return LockResolution.isLocked(scope, gating.stages(), state);
    }

    /**
     * Works the answer out and remembers it when it is safe to.
     *
     * <p>"Safe" means the answer cannot differ between two stacks of the same item — see
     * {@link #dependsOnTheStack}. Remembering a stack-dependent answer would serve one enchanted
     * sword's verdict for every plain one, which is the kind of fault that looks like a config
     * mistake rather than a cache.
     */
    private CategoryLockIndexes.ItemGating computeItemGating(String itemId, String modId,
                                                             @Nullable ItemStack stack, StageScope scope,
                                                             StackFacts facts) {
        Item item = stack != null ? stack.getItem() : BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        // Resolved once and threaded through both uses: the capability lookup is not free, and
        // the four-argument ItemSubject constructor would repeat it.
        String fluidId = facts.fluidId();
        Collection<String> candidates = scope == StageScope.GLOBAL
                ? CategoryLockIndexes.globalCandidates(itemId, modId, item, fluidId, facts.contents())
                : CategoryLockIndexes.individualCandidates(itemId, modId, item, fluidId, facts.contents());

        Map<String, StageEntry> stages = stagesOf(scope);
        List<String> gating = candidates.isEmpty() ? List.of()
                : CategoryLockResolver.gatingStages(itemCategories(),
                        new LockSubjects.ItemSubject(itemId, modId, stack, item, fluidId, facts.contents()),
                        candidates, stages);

        CategoryLockIndexes.ItemGating answer = gatingMasks(gating, scope);

        if (facts.memoUsable() && !dependsOnTheStack(candidates, stages, itemId, fluidId)) {
            CategoryLockIndexes.rememberItemGating(itemId, scope, answer);
        }
        return answer;
    }

    /**
     * The gating list with its two masks. Split at build time so the per-frame check stays a few
     * AND operations; the flagged set is read once here, not per question.
     */
    private static CategoryLockIndexes.ItemGating gatingMasks(List<String> gating, StageScope scope) {
        if (gating.isEmpty()) {
            return new CategoryLockIndexes.ItemGating(gating, StageMask.EMPTY, StageMask.EMPTY);
        }
        StageIndex index = CategoryLockIndexes.stageIndex();
        Set<String> flagged = InterchangeableStages.of(scope);
        if (flagged.isEmpty()) {
            return new CategoryLockIndexes.ItemGating(gating, StageMask.of(index, gating), StageMask.EMPTY);
        }
        List<String> required = new ArrayList<>(gating.size());
        List<String> anyOf = new ArrayList<>(2);
        for (String stage : gating) {
            (flagged.contains(stage) ? anyOf : required).add(stage);
        }
        return new CategoryLockIndexes.ItemGating(gating,
                StageMask.of(index, required), StageMask.of(index, anyOf));
    }

    /**
     * Whether any candidate stage decides this item by something only a stack carries.
     *
     * <p>Scanned over the candidates, which the relevance index has already narrowed to a
     * handful, and only on a miss. Erring towards "yes" costs a recomputation; erring towards
     * "no" caches a wrong answer, so every NBT-bearing shape counts — an item entry for this id,
     * any tag entry at all, and a mod exception for this id.
     *
     * <p>A fluid entry counts too, and for a reason the item id cannot show: a modded tank item
     * keeps one id while holding whatever was last put in it. Caching by id would serve the
     * verdict for a tank of gated lava to the identical empty tank beside it.
     */
    private static boolean dependsOnTheStack(Collection<String> candidates,
                                             Map<String, StageEntry> stages, String itemId,
                                             String fluidId) {
        for (String stageId : candidates) {
            StageEntry stage = stages.get(stageId);
            if (stage == null) continue;
            if (!stage.getFluidEntries().isEmpty()) return true;
            for (net.bananemdnsa.historystages.data.ItemEntry entry : stage.getItemEntries()) {
                if (entry.hasNbt() && entry.getId().equals(itemId)) return true;
            }
            for (net.bananemdnsa.historystages.data.lock.NamedLockEntry tag : stage.getTagEntries()) {
                if (tag.hasNbt()) return true;
            }
            for (net.bananemdnsa.historystages.data.ItemEntry exception : stage.getModExceptionEntries()) {
                if (exception.hasNbt() && exception.getId().equals(itemId)) return true;
            }
        }
        return false;
    }

    /**
     * The fluid this stack is carrying, asked only when some stage would do something with the
     * answer.
     *
     * <p>Finding it out means calling into whichever mod owns the item, through the fluid
     * capability. That is foreign code on the crafting path — once per station per tick, for every
     * item — in a pack that may not gate a single fluid, and the recipe walk reaches it early
     * enough in a world load that the mod being asked may not have its own config yet (#130).
     */
    @Nullable
    private static String fluidOf(@Nullable ItemStack stack) {
        return CategoryLockIndexes.anyStageUses("historystages:fluids")
                ? FluidContent.of(stack) : null;
    }

    /**
     * Enchantments and potion contents, read only when some stage could act on them.
     *
     * <p>Mod entries count, because a book or potion belongs to the mod of what it carries. They
     * do not care about gear enchantments, so without an enchantment entry anywhere those are
     * dropped and a plain enchanted sword keeps the per-id memo.
     */
    private static StackContents contentsOf(@Nullable ItemStack stack) {
        if (stack == null) return StackContents.EMPTY;
        boolean enchantments = CategoryLockIndexes.anyStageUses("historystages:enchantments");
        boolean effects = CategoryLockIndexes.anyStageUses("historystages:effects");
        boolean mods = CategoryLockIndexes.anyStageUses("historystages:mods");
        if (!enchantments && !effects && !mods) return StackContents.EMPTY;

        StackContents contents = StackContentsReader.of(stack);
        if (!enchantments && !contents.enchantments().isEmpty()) {
            contents = new StackContents(List.of(), contents.storedEnchantments(),
                    contents.effects(), contents.potionIds());
            if (contents.isEmpty()) return StackContents.EMPTY;
        }
        return contents;
    }

    private static List<LockCategory<?>> itemCategories() {
        List<LockCategory<?>> categories = new ArrayList<>(ITEM_CATEGORY_IDS.size());
        for (String id : ITEM_CATEGORY_IDS) categories.add(category(id));
        return categories;
    }

    @Override
    public List<String> globalDualPhaseStagesForItem(String itemId, String modId, @Nullable Item item) {
        List<String> stages = new ArrayList<>();

        Set<String> itemStages = CategoryLockIndexes.dualPhaseGlobal("historystages:items").get(itemId);
        if (itemStages != null) stages.addAll(itemStages);

        Set<String> modStages = CategoryLockIndexes.dualPhaseGlobal("historystages:mods").get(modId);
        if (modStages != null) stages.addAll(modStages);

        if (item != null) {
            for (Map.Entry<String, Set<String>> tagEntry : CategoryLockIndexes.dualPhaseGlobal("historystages:tags").entrySet()) {
                TagKey<Item> tagKey = TagKey.create(Registries.ITEM, ResourceLocation.parse(tagEntry.getKey()));
                if (item.builtInRegistryHolder().is(tagKey)) stages.addAll(tagEntry.getValue());
            }
        }

        return stages;
    }

    @Override
    public List<String> gatingStagesForItemAction(String itemId, String modId,
                                                  @Nullable ItemStack stack, String action,
                                                  StageScope scope) {
        List<String> gating = gatingStagesForItem(itemId, modId, stack, scope);
        if (gating.isEmpty()) return gating;

        Map<String, StageEntry> stages = stagesOf(scope);
        StackFacts facts = factsOf(stack);
        LockSubjects.ItemSubject subject = new LockSubjects.ItemSubject(
                itemId, modId, stack, stack != null ? stack.getItem() : null,
                facts.fluidId(), facts.contents());

        List<String> narrowed = new ArrayList<>(gating.size());
        for (String stageId : gating) {
            StageEntry entry = stages.get(stageId);
            if (entry != null && ItemActionLocks.isBlockedBy(entry, subject, action)) {
                narrowed.add(stageId);
            }
        }
        return narrowed;
    }

    @Override
    public boolean isItemActionLocked(ItemStack stack, String action, StageScope scope, StageStateView state) {
        ResourceLocation res = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (res == null) return false;
        String itemId = res.toString();
        String modId = res.getNamespace();
        StackFacts facts = factsOf(stack);
        String fluidId = facts.fluidId();

        boolean global = scope == StageScope.GLOBAL;
        Collection<String> candidates = global
                ? CategoryLockIndexes.globalCandidates(itemId, modId, stack.getItem(), fluidId, facts.contents())
                : CategoryLockIndexes.individualCandidates(itemId, modId, stack.getItem(), fluidId, facts.contents());
        // Nothing to ask, so nothing to build the question with. This is the answer for almost
        // every item, and the recipe path asks it once per furnace per tick.
        if (candidates.isEmpty()) return false;

        Map<String, StageEntry> stages = stagesOf(scope);
        LockSubjects.ItemSubject subject = new LockSubjects.ItemSubject(
                itemId, modId, stack, stack.getItem(), fluidId, facts.contents());

        return LockResolution.isLocked(scope, actionGating(candidates, stages, subject, action), state);
    }

    @Override
    public boolean isFluidActionLocked(String fluidId, String action, StageScope scope,
                                       StageStateView state) {
        if (fluidId == null) return false;

        // Empty item id and mod id on purpose: no stage lists those, so the item and mod halves
        // of the narrowing contribute nothing and the fluid half is the whole answer. The same
        // emptiness carries into the subject, where it makes the item and mod loops in
        // ItemActionLocks fall straight through to the fluid one.
        boolean global = scope == StageScope.GLOBAL;
        Collection<String> candidates = global
                ? CategoryLockIndexes.globalCandidates("", "", null, fluidId)
                : CategoryLockIndexes.individualCandidates("", "", null, fluidId);
        if (candidates.isEmpty()) return false;

        Map<String, StageEntry> stages = stagesOf(scope);
        LockSubjects.ItemSubject subject =
                new LockSubjects.ItemSubject("", "", null, null, fluidId);

        return LockResolution.isLocked(scope, actionGating(candidates, stages, subject, action), state);
    }

    /**
     * The candidates that really block this action. Collected in full rather than stopping at the
     * first locked one: with interchangeable stages an unlocked stage can still settle the answer,
     * so the whole group has to be known before {@link LockResolution} decides.
     */
    private static List<String> actionGating(Collection<String> candidates, Map<String, StageEntry> stages,
                                             LockSubjects.ItemSubject subject, String action) {
        List<String> found = null;
        for (String stageId : candidates) {
            StageEntry entry = stages.get(stageId);
            if (entry == null) continue;
            if (ItemActionLocks.isBlockedBy(entry, subject, action)) {
                if (found == null) found = new ArrayList<>(1);
                found.add(stageId);
            }
        }
        return found == null ? List.of() : found;
    }

    @Override
    public List<String> gatingStagesForRecipe(String recipeId, StageScope scope) {
        return narrowed("historystages:recipes", recipeId, scope);
    }

    @Override
    public List<String> gatingStagesForDimension(String dimensionId, StageScope scope) {
        return narrowed("historystages:dimensions", dimensionId, scope);
    }

    @Override
    public List<String> gatingStagesForStructure(String structureId, StageScope scope) {
        return narrowed("historystages:structures", structureId, scope);
    }

    @Override
    public List<String> gatingStagesForEntityAttack(String entityId, StageScope scope) {
        return narrowed("historystages:attacklock", entityId, scope);
    }

    @Override
    public List<String> gatingStagesForEntityInteraction(String entityId, String action,
                                                         ItemStack held, StageScope scope) {
        return narrowed("historystages:interactionlock",
                new LockSubjects.InteractionSubject(entityId, action, held), scope);
    }

    @Override
    public List<String> gatingStagesForEntitySpawn(String entityId, String source,
                                                   String dimension, StageScope scope) {
        // Individual spawn locks do not exist in the data model. Returning empty here is what
        // the pre-seam code effectively did; closing that gap is a separate change.
        if (scope == StageScope.INDIVIDUAL) return List.of();
        return narrowed("historystages:spawnlock",
                new LockSubjects.SpawnSubject(entityId, source, dimension), scope);
    }

    @Override
    public List<String> gatingStagesWithSpawnEntry(String entityId, String dimension, StageScope scope) {
        if (scope == StageScope.INDIVIDUAL) return List.of();
        // A null source is the "any source" question — see LockSubjects.SpawnSubject.
        return narrowed("historystages:spawnlock",
                new LockSubjects.SpawnSubject(entityId, null, dimension), scope);
    }

    @Override
    public List<String> gatingStagesForEnchantment(String enchantmentId, int level, String station,
                                                   @Nullable String itemId, StageScope scope) {
        List<String> found = new ArrayList<>();
        for (Map.Entry<String, StageEntry> entry : stagesOf(scope).entrySet()) {
            StageEntry stage = entry.getValue();
            boolean gated = EnchantmentLockMatcher.locksEnchantment(stage, enchantmentId, level)
                    || modGatesStation(stage, enchantmentId);
            for (var e : stage.getEnchantmentEntries()) {
                if (gated) break;
                gated = (itemId == null || !e.excludes(itemId))
                        && LevelledMatching.locksStation(e, stage.effectiveEnchantmentLockActions(e.getLockActions()),
                        enchantmentId, level, station);
            }
            if (gated) found.add(entry.getKey());
        }
        return found;
    }

    @Override
    public List<String> gatingStagesForEffect(String effectId, int level, String itemId, StageScope scope) {
        List<String> found = new ArrayList<>();
        for (Map.Entry<String, StageEntry> entry : stagesOf(scope).entrySet()) {
            StageEntry stage = entry.getValue();
            boolean gated = modGatesStation(stage, effectId);
            for (var e : stage.getEffectEntries()) {
                if (gated) break;
                gated = !e.excludes(itemId) && LevelledMatching.locksStation(e,
                        stage.effectiveEffectLockActions(e.getLockActions()), effectId, level, "brew");
            }
            if (gated) found.add(entry.getKey());
        }
        return found;
    }

    /**
     * A mod entry refuses its mod's enchantments and effects at a station only while it locks
     * making things. An entry narrowed to, say, pickup says nothing about the enchanting table,
     * and reading it as a full lock is the mistake Issue #117 was about.
     */
    private static boolean modGatesStation(StageEntry stage, String id) {
        String namespace = StackContents.namespaceOf(id);
        for (var mod : stage.getModEntries()) {
            if (!mod.getId().equals(namespace)) continue;
            List<String> actions = stage.effectiveItemLockActions(mod.getLockActions());
            if (actions == null || actions.contains("recipe")) return true;
        }
        return false;
    }

    @Override
    public boolean anyStructureLocks() {
        return CategoryLockIndexes.anyStageUses("historystages:structures");
    }

    @Override
    public boolean anyEntitySpawnLocks() {
        return CategoryLockIndexes.anyStageUses("historystages:spawnlock");
    }

    @Override
    public boolean anyBiomeLocks() {
        return CategoryLockIndexes.anyStageUses("historystages:biomes");
    }

    @Override
    public boolean anyZoneLocks() {
        return CategoryLockIndexes.anyStageUses("historystages:zones");
    }

    public StringStageLockEngine() {
        InterchangeableStages.source(StringStageLockEngine::stagesOf);
    }

    @Override
    public void stagesChanged() {
        CategoryLockIndexes.markRelevanceDirty();
        InterchangeableStages.markDirty();
        // Stages do not change what a recipe contains, only whether the fluid recipe index is
        // worth having — so this is a relevance signal, not a re-scan. A pack adding its first
        // fluid entry still gets one built; the editor no longer re-encodes the pack per save.
        net.bananemdnsa.historystages.data.lock.FluidRecipeIndex.markRelevanceDirty();
        // Where a zone lies is stage data, so editing a stage is the only thing that can move one.
        // Hanging the zone index here is what saves the handler an invalidation path of its own,
        // the way the biome handler needs one: every write to the stage store already comes
        // through here.
        net.bananemdnsa.historystages.data.lock.ZoneIndex.markDirty();
    }


    /**
     * One category, narrowed through its own index where it has one.
     *
     * <p>The index answers "which stages could possibly match this key", and only those are then
     * asked properly. A category that does not index itself falls through to the full scan, which
     * is what every category did before Phase 10 — correct, just linear in the number of stages,
     * and that is four microseconds at the three hundred a real pack ships.
     */
    private static List<String> narrowed(String categoryId, Object subject, StageScope scope) {
        LockCategory<?> category = category(categoryId);
        List<String> candidates =
                CategoryLockIndexes.candidates(categoryId, scope, category.lookupKey(subject));
        if (candidates == null) {
            return CategoryLockResolver.gatingStages(category, subject, stagesOf(scope));
        }
        if (candidates.isEmpty()) return List.of();
        return CategoryLockResolver.gatingStages(List.of(category), subject, candidates, stagesOf(scope));
    }

    /** The stage map for a scope — the one thing this class still asks the store for. */
    private static Map<String, StageEntry> stagesOf(StageScope scope) {
        return scope == StageScope.GLOBAL ? StageManager.getStages() : StageManager.getIndividualStages();
    }

    /**
     * Fails loudly rather than quietly unlocking everything, which is what a missing category
     * would otherwise do — a null here would turn into "nothing gates this".
     */
    private static LockCategory<?> category(String id) {
        LockCategory<?> found = LockCategories.byId(id);
        if (found == null) throw new IllegalStateException("built-in lock category missing: " + id);
        return found;
    }
}
