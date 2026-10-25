package net.bananemdnsa.historystages.api.lock;

import net.bananemdnsa.historystages.api.stage.StageScope;

/**
 * One stored entry that matches a subject, on one stage, with where the viewer stands on it.
 *
 * <p>What {@link CategoryLocks#hits} returns when a yes-or-no is not enough: an addon that lets
 * the most specific entry win, or whose entries can grant as well as deny, has to see the entries
 * themselves.
 *
 * <p>Two flags, because they answer different questions. {@code unlocked} is the plain fact
 * about this one stage. {@code locks} is the verdict for this hit, with interchangeable stages
 * counted in: once any stage of an interchangeable group that gates the subject is unlocked, the
 * other stages of that group stop standing in the way, even though they are still locked. A hit
 * with {@code locks == true} is exactly a stage that {@link CategoryLocks#missingStagesForPlayer}
 * would name. An addon deciding whether to block wants {@code locks}; one granting on an unlocked
 * stage wants {@code unlocked}.
 *
 * @param stageId  the stage holding the entry
 * @param scope    which tree that stage belongs to
 * @param entry    the stored entry, of the category's own entry type
 * @param unlocked true if the viewer has the stage: the world for a global one, the player for an
 *                 individual one
 * @param locks    true if this stage still keeps the subject locked for the viewer: it is missing,
 *                 and no unlocked interchangeable stage stands in for it
 * @param <T>      the category's entry type
 */
public record LockHit<T>(String stageId, StageScope scope, T entry, boolean unlocked, boolean locks) {}
