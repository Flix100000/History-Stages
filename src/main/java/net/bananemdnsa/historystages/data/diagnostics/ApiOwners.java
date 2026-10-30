package net.bananemdnsa.historystages.data.diagnostics;

import org.jetbrains.annotations.Nullable;

/** Reads which mod's listener is running while History Stages fires one of its register events. */
public final class ApiOwners {

    private static @Nullable String active;

    private ApiOwners() {}

    /**
     * NeoForge reads this from the mod loading context. Fabric has no such context, so the
     * registration window sets it while it calls each addon's {@code historystages} entrypoint.
     * Listeners on the mod's own bus run outside that and are reported as null, the same as a
     * NeoForge event fired outside its wrapped dispatch.
     */
    public static @Nullable String active() {
        return active;
    }

    /** Runs {@code body} with {@code modId} as the active mod. Only ever called on the main thread. */
    public static void runAs(String modId, Runnable body) {
        String previous = active;
        active = modId;
        try {
            body.run();
        } finally {
            active = previous;
        }
    }
}
