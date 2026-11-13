package net.bananemdnsa.historystages.platform.fabric;

import dev.architectury.utils.GameInstance;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/** Fabric side of {@code platform.LoaderContext}. */
public final class LoaderContextImpl {

    private static @Nullable String active;

    private LoaderContextImpl() {}

    /**
     * NeoForge answers this from the thread itself. Fabric has nothing like it, so it is worked out
     * the way it is defined: a dedicated server is never a client, and on a client everything but
     * the integrated server's own thread is. Without a running server only the client is left.
     */
    public static boolean isEffectiveClient() {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
            return false;
        }
        MinecraftServer server = GameInstance.getServer();
        return server == null || Thread.currentThread() != server.getRunningThread();
    }

    public static @Nullable String activeModId() {
        return active;
    }

    /**
     * Runs {@code body} with {@code modId} as the active mod. Fabric has no mod loading context,
     * so whoever calls an addon's entrypoint sets it here. Main thread only.
     */
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
