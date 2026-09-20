package net.bananemdnsa.historystages.data.saveddata;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Stamps an unlock with the overworld's game time. Not the day time: /time set moves that
 * backwards, and an unlock would then look older than one made before it.
 */
final class UnlockClock {

    private UnlockClock() {}

    /** Null without a running server; such an unlock is simply treated as having no time. */
    static Long now() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.overworld().getGameTime();
    }
}
