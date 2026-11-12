package net.bananemdnsa.historystages.platform.bus;

/**
 * Base class for everything the mod's own event bus carries.
 *
 * <p>The handlers are written against NeoForge's bus: dozens of classes declare
 * {@code @SubscribeEvent} methods that take a rich event object and answer by setting fields on
 * it. Carrying that shape into common code means the handlers stay the same on both loaders,
 * which matters because they are where the mod's actual rules live.
 *
 * <p>The events themselves are not raised by this class. Each loader's bridge fires them from the
 * native event, callback or mixin that corresponds to it, and that side is where the loaders
 * really differ.
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
