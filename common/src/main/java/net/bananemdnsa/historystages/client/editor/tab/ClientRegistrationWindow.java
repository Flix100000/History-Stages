package net.bananemdnsa.historystages.client.editor.tab;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Opens the client registration window after the common one, whichever way round the loader
 * starts things.
 *
 * <p>On Fabric both windows hang off Architectury: SETUP and then CLIENT_SETUP, fired from
 * Architectury's own client entrypoint. Fabric does not promise that entrypoint runs after ours, and
 * Architectury does not replay an event to late listeners. Registering on CLIENT_SETUP alone missed
 * it whenever Architectury went first, and then no addon editor tab existed and the registries
 * never closed.
 */
public final class ClientRegistrationWindow {

    private ClientRegistrationWindow() {}

    /**
     * Runs {@code window} now when the common window has already run, otherwise hands a runner to
     * {@code onCommonWindowDone}. Either way it runs at most once.
     */
    public static void openAfterCommonWindow(boolean commonWindowDone, Consumer<Runnable> onCommonWindowDone,
                                             Runnable window) {
        AtomicBoolean opened = new AtomicBoolean();
        Runnable once = () -> {
            if (opened.compareAndSet(false, true)) window.run();
        };
        if (commonWindowDone) {
            once.run();
        } else {
            onCommonWindowDone.accept(once);
        }
    }
}
