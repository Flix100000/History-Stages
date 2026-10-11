package net.bananemdnsa.historystages.api.lock;

import net.bananemdnsa.historystages.api.stage.StageScope;

/**
 * One stored entry that matches a subject, on one stage, and whether the viewer already has that
 * stage.
 *
 * <p>What {@link CategoryLocks#hits} returns when a yes-or-no is not enough: an addon that lets
 * the most specific entry win, or whose entries can grant as well as deny, has to see the entries
 * themselves.
 *
 * <p>These are raw facts, not a verdict. Interchangeable stages, where one unlocked stage of a
 * group settles the others, are only applied by {@link CategoryLocks#isLockedForPlayer} and
 * {@link CategoryLocks#missingStagesForPlayer}. Ask those for the plain answer.
 *
 * @param stageId  the stage holding the entry
 * @param scope    which tree that stage belongs to
 * @param entry    the stored entry, of the category's own entry type
 * @param unlocked true if the viewer has the stage: the world for a global one, the player for an
 *                 individual one
 * @param <T>      the category's entry type
 */
public record LockHit<T>(String stageId, StageScope scope, T entry, boolean unlocked) {}
