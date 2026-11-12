package net.bananemdnsa.historystages.platform.bus;

/**
 * Marks an event a handler is allowed to stop.
 *
 * <p>What cancelling means is decided by whoever fires the event, not here: for an interaction it
 * turns into a FAIL result, for a spawn it means the mob is not placed. The bus itself only
 * remembers the flag and stops passing the event on.
 */
public interface ICancellableEvent {

    default boolean isCanceled() {
        return ((Event) this).isCanceledInternal();
    }

    default void setCanceled(boolean canceled) {
        ((Event) this).setCanceledInternal(canceled);
    }
}
