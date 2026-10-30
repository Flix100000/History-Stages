package net.bananemdnsa.historystages.api.lock;

import net.bananemdnsa.historystages.data.lock.category.CategoryLockResolver;
import net.bananemdnsa.historystages.data.lock.category.CategorySubjects;
import net.bananemdnsa.historystages.data.lock.category.LockCategories;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.lock.engine.StageLocks;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.stage.StageStateView;
import org.jetbrains.annotations.Nullable;

/**
 * What an addon calls to ask whether something is gated by a category — one of its own, or a
 * built-in one.
 *
 * <p>The addon wires its own game hook — HistoryStages has no idea when a villager trade is about
 * to be offered — and asks here. Both scopes are consulted: a subject is locked when a global
 * stage gating it is not yet unlocked for the world, or an individual stage gating it is not yet
 * unlocked for this player.
 *
 * <p><strong>Subjects.</strong> An addon category gets exactly what is passed. The built-ins
 * accept the obvious things: an id string for dimensions, recipes, structures, biomes, trade
 * levels and attack locks; an {@code ItemStack}, {@code Item} or item id for items, tags and mods;
 * a fluid id, {@code Fluid} or {@code FluidVariant} for fluids. Registry handles
 * ({@code ResourceLocation}, {@code ResourceKey}, {@code Holder}) work wherever an id does. The
 * rest — spawns, interactions, merchants, single offers, levelled enchantments — come from
 * {@link Subjects}. For items there is also {@link ItemLocks}, which is faster and knows about
 * lock actions.
 *
 * <p>Deliberately thin. Every decision worth getting wrong lives in {@link CategoryLockResolver},
 * which is free of Minecraft and therefore properly tested; this class only supplies the stage
 * maps and the right viewer. If a branch ever wants to grow here, it belongs there instead.
 */
public final class CategoryLocks {

    private CategoryLocks() {}

    /** Server side: is this subject gated for this player, in either scope? */
    public static boolean isLockedForPlayer(String categoryId, Object subject, UUID playerUuid) {
        return isLocked(categoryId, subject, StageLocks.serverIndividual(playerUuid));
    }

    /**
     * Server side, global stages only: is this subject gated for the whole world? For paths that
     * have no player, such as a machine working on its own.
     */
    public static boolean isLockedGlobally(String categoryId, Object subject) {
        return isLocked(categoryId, subject, null);
    }

    /**
     * Server side: the stages this player still needs before the subject becomes available,
     * global ones first. Empty when the subject is not gated at all.
     */
    public static List<String> missingStagesForPlayer(String categoryId, Object subject, UUID playerUuid) {
        return missingStages(categoryId, subject, StageLocks.serverIndividual(playerUuid));
    }

    /** Server side, global stages only: the stages the world still needs. */
    public static List<String> missingStagesGlobally(String categoryId, Object subject) {
        return missingStages(categoryId, subject, null);
    }

    /**
     * Server side: every stored entry that matches the subject, global stages first, each with
     * whether this player already has its stage. Empty when nothing matches.
     *
     * <p>For an addon whose entries do more than lock: one where the most specific entry wins, or
     * where an entry can also grant. Each hit says both whether its stage is unlocked and whether
     * it still locks the subject once interchangeable stages are counted in; see {@link LockHit}.
     */
    public static <T> List<LockHit<T>> hits(LockCategory<T> category, Object subject, UUID playerUuid) {
        Object normalized = CategorySubjects.normalize(category.id(), subject);
        List<LockHit<T>> global = CategoryLockResolver.supports(category, StageScope.GLOBAL)
                ? CategoryLockResolver.hits(category, normalized,
                        StageManager.getStages(), StageScope.GLOBAL, StageLocks.serverGlobal())
                : List.of();
        List<LockHit<T>> individual = CategoryLockResolver.supports(category, StageScope.INDIVIDUAL)
                ? CategoryLockResolver.hits(category, normalized,
                        StageManager.getIndividualStages(), StageScope.INDIVIDUAL, StageLocks.serverIndividual(playerUuid))
                : List.of();
        if (individual.isEmpty()) return global;
        if (global.isEmpty()) return individual;
        List<LockHit<T>> all = new ArrayList<>(global);
        all.addAll(individual);
        return all;
    }

    /** {@link #hits(LockCategory, Object, UUID)} by category id. Empty when nothing is registered under it. */
    public static List<LockHit<?>> hits(String categoryId, Object subject, UUID playerUuid) {
        LockCategory<?> category = LockCategories.byId(categoryId);
        if (category == null) return List.of();
        return List.copyOf(hits(category, subject, playerUuid));
    }

    /** Null when nothing is registered under this id — lets a mod check that it registered in time. */
    @Nullable
    public static LockCategory<?> category(String categoryId) {
        return LockCategories.byId(categoryId);
    }

    /** Every registered category id, built-ins first, in editor tab order. */
    public static List<String> categoryIds() {
        return LockCategories.ids();
    }

    // A null individual view means "global only": the individual half is not asked at all.

    private static boolean isLocked(String categoryId, Object subject, @Nullable StageStateView individual) {
        LockCategory<?> category = LockCategories.byId(categoryId);
        if (category == null) return false;
        Object normalized = CategorySubjects.normalize(categoryId, subject);

        // A category that means nothing per player is not asked about individual stages; the
        // answer would be meaningless rather than merely empty.
        return (CategoryLockResolver.supports(category, StageScope.GLOBAL)
                        && CategoryLockResolver.isLocked(category, normalized,
                                StageManager.getStages(), StageScope.GLOBAL, StageLocks.serverGlobal()))
                || (individual != null && CategoryLockResolver.supports(category, StageScope.INDIVIDUAL)
                        && CategoryLockResolver.isLocked(category, normalized,
                                StageManager.getIndividualStages(), StageScope.INDIVIDUAL, individual));
    }

    private static List<String> missingStages(String categoryId, Object subject, @Nullable StageStateView individual) {
        LockCategory<?> category = LockCategories.byId(categoryId);
        if (category == null) return List.of();
        Object normalized = CategorySubjects.normalize(categoryId, subject);

        return CategoryLockResolver.join(
                CategoryLockResolver.supports(category, StageScope.GLOBAL)
                        ? CategoryLockResolver.missingStages(category, normalized,
                                StageManager.getStages(), StageScope.GLOBAL, StageLocks.serverGlobal())
                        : List.of(),
                individual != null && CategoryLockResolver.supports(category, StageScope.INDIVIDUAL)
                        ? CategoryLockResolver.missingStages(category, normalized,
                                StageManager.getIndividualStages(), StageScope.INDIVIDUAL, individual)
                        : List.of());
    }
}
