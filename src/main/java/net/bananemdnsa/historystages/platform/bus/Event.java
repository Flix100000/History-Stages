package net.bananemdnsa.historystages.platform.bus;

/**
 * Base class for everything the mod's own event bus carries.
 *
 * <p>Fabric has no event bus, but this mod is written against one: 41 handler classes declare
 * {@code @SubscribeEvent} methods that take a rich event object and answer by setting fields on
 * it. Rebuilding that here means those 4665 lines of handler stay the same on both loaders, which
 * is worth far more than a Fabric-shaped rewrite would be — the handlers are where the mod's
 * actual rules live, and they are the last thing that should drift between branches.
 *
 * <p>The events themselves are not raised by this class. Each one is fired from the Fabric
 * callback or mixin that corresponds to it, and that side is where the loaders really differ.
 */
public abstract class Event {

    private boolean canceled;

    /**
     * Not public: only {@link ICancellableEvent} exposes this, so an event that was never meant to
     * be stoppable cannot be stopped by accident.
     */
    boolean isCanceledInternal() {
        return canceled;
    }

    void setCanceledInternal(boolean canceled) {
        this.canceled = canceled;
    }
}
