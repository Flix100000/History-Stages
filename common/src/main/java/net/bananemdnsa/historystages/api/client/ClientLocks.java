package net.bananemdnsa.historystages.api.client;

import java.util.List;

import net.bananemdnsa.historystages.api.lock.CategoryLocks;
import net.bananemdnsa.historystages.api.lock.LockCategory;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.client.cache.ClientStageStates;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.lock.category.CategoryLockResolver;
import net.bananemdnsa.historystages.data.lock.category.CategorySubjects;
import net.bananemdnsa.historystages.data.lock.category.LockCategories;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.minecraft.world.item.ItemStack;

/**
 * The client-side counterpart of {@link CategoryLocks} and {@code ItemLocks}, for the local
 * player: to grey something out or write a tooltip before the server has been asked.
 *
 * <p><strong>Client only</strong>, for the same reason as {@link ClientStages}. It is a separate
 * class from {@code CategoryLocks} on purpose: that one is reachable from server code, and
 * pulling the client caches into it is how the crash fixed in commit 0469f73 happened.
 *
 * <p>Subjects are the same as for {@link CategoryLocks}.
 */
public final class ClientLocks {

    private ClientLocks() {}

    /** Is this subject gated for the local player, in either scope? */
    public static boolean isLocked(String categoryId, Object subject) {
        LockCategory<?> category = LockCategories.byId(categoryId);
        if (category == null) return false;
        Object normalized = CategorySubjects.normalize(categoryId, subject);

        return (CategoryLockResolver.supports(category, StageScope.GLOBAL)
                        && CategoryLockResolver.isLocked(category, normalized,
                                StageManager.getStages(), StageScope.GLOBAL, ClientStageStates.global()))
                || (CategoryLockResolver.supports(category, StageScope.INDIVIDUAL)
                        && CategoryLockResolver.isLocked(category, normalized,
                                StageManager.getIndividualStages(), StageScope.INDIVIDUAL, ClientStageStates.individual()));
    }

    /** The stages the local player still needs, global ones first. */
    public static List<String> missingStages(String categoryId, Object subject) {
        LockCategory<?> category = LockCategories.byId(categoryId);
        if (category == null) return List.of();
        Object normalized = CategorySubjects.normalize(categoryId, subject);

        return CategoryLockResolver.join(
                CategoryLockResolver.supports(category, StageScope.GLOBAL)
                        ? CategoryLockResolver.missingStages(category, normalized,
                                StageManager.getStages(), StageScope.GLOBAL, ClientStageStates.global())
                        : List.of(),
                CategoryLockResolver.supports(category, StageScope.INDIVIDUAL)
                        ? CategoryLockResolver.missingStages(category, normalized,
                                StageManager.getIndividualStages(), StageScope.INDIVIDUAL, ClientStageStates.individual())
                        : List.of());
    }

    /**
     * Does a stage the local player is missing gate this stack for any action? Ignores action
     * narrowing, so it suits a tooltip; to predict a block, ask {@link #isItemActionLocked}.
     */
    public static boolean isItemGated(ItemStack stack) {
        return StageLockHelper.isItemLockedForClient(stack);
    }

    /** May the local player not do {@code action} with this stack? */
    public static boolean isItemActionLocked(ItemStack stack, String action) {
        return StageLockHelper.isItemActionLockedForClient(stack, action);
    }
}
