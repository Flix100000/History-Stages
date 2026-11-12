package net.bananemdnsa.historystages.api.lock;

/**
 * Something a built-in category can be asked about that is more than a bare id: an item stack, a
 * spawn attempt, an interaction, a merchant, one trade offer.
 *
 * <p>Build one with {@link Subjects} and hand it to {@link CategoryLocks}. The implementations
 * are internal and may change shape; this type is the promise.
 */
public interface LockSubject {}
