package net.bananemdnsa.historystages.data.lock.engine;

import java.util.List;

import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.NbtMatcher;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.NamedLockEntry;
import net.bananemdnsa.historystages.data.lock.category.BuiltInLockMatching;

/**
 * Whether one specific action on an item is blocked — the one lock question that is deliberately
 * not a {@link net.bananemdnsa.historystages.api.lock.LockCategory#gates} question.
 *
 * <p>{@code gates} answers "does this stage lock this?". This asks something narrower and
 * order-dependent: <em>which entry matches first</em> — items, then mods, then tags — and what
 * does <em>that</em> entry say about this action. The first match decides even when it says
 * "allowed", so a later entry that would have blocked never gets a vote. Lifting that precedence
 * into the category contract would impose it on every addon, and no addon needs it.
 */
public final class ItemActionLocks {

    private ItemActionLocks() {}

    /**
     * True when {@code action} is blocked for this subject by this stage. False when the item
     * does not match the stage at all, and equally false when the first matching entry allows
     * the action.
     */
    public static boolean isBlockedBy(StageEntry stage, LockSubjects.ItemSubject subject, String action) {
        for (ItemEntry entry : stage.getItemEntries()) {
            if (!entry.getId().equals(subject.itemId())) continue;
            boolean nbtMatch = !entry.hasNbt()
                    || (subject.stack() != null && NbtMatcher.matches(subject.stack(), entry.getNbt()));
            if (nbtMatch) return isActionInList(stage.effectiveItemLockActions(entry.getLockActions()), action);
        }

        // Right after items and before mods: naming what the container holds is a statement
        // about this exact stack, closer to an id than to a namespace. An item entry for the
        // bucket itself still wins, so a pack can carve out one container of a gated fluid.
        for (net.bananemdnsa.historystages.data.FluidEntry fluidEntry : stage.getFluidEntries()) {
            if (BuiltInLockMatching.fluidEntryMatches(fluidEntry, subject)) {
                return isActionInList(stage.effectiveFluidLockActions(fluidEntry.getLockActions()), action);
            }
        }

        // Then what the stack carries in enchantments and effects, for the same reason fluids sit
        // here: it describes this exact stack. These entries carry no action list of their own,
        // so the stage's fixed item actions decide.
        for (net.bananemdnsa.historystages.data.LevelledLockEntry entry : stage.getEnchantmentEntries()) {
            if (LevelledMatching.locksItem(entry, subject.contents().allEnchantments())) {
                return isActionInList(stage.effectiveItemLockActions(null), action);
            }
        }
        for (net.bananemdnsa.historystages.data.LevelledLockEntry entry : stage.getEffectEntries()) {
            if (LevelledMatching.locksItem(entry, subject.contents().effects())) {
                return isActionInList(stage.effectiveItemLockActions(null), action);
            }
        }

        for (NamedLockEntry modEntry : stage.getModEntries()) {
            if (BuiltInLockMatching.belongsToMod(modEntry.getId(), subject)
                    && !stage.isModExcepted(subject.itemId(), subject.stack())) {
                return isActionInList(stage.effectiveItemLockActions(modEntry.getLockActions()), action);
            }
        }

        if (subject.item() != null) {
            for (NamedLockEntry tagEntry : stage.getTagEntries()) {
                if (BuiltInLockMatching.tagEntryMatches(tagEntry, subject)) {
                    return isActionInList(stage.effectiveItemLockActions(tagEntry.getLockActions()), action);
                }
            }
        }

        return false;
    }

    /**
     * null = every action is locked, which is the default and what a missing {@code unlock_actions}
     * in the JSON means. An empty list = nothing is locked. A non-empty list = only the listed
     * actions are locked.
     */
    private static boolean isActionInList(List<String> lockActions, String action) {
        if (lockActions == null) return true;
        return lockActions.contains(action);
    }
}
