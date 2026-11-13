package net.bananemdnsa.historystages.platform;

import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import dev.architectury.utils.GameInstance;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * The few questions about the running loader that common code asks: which side this is, whether
 * a mod is present, where the config and game folders are. Stands in for NeoForge's
 * {@code FMLEnvironment}, {@code ModList} and {@code FMLPaths}.
 */
public final class Services {

    private Services() {}

    /** True on the physical client, including the integrated server running inside it. */
    public static boolean isClient() {
        return Platform.getEnvironment() == Env.CLIENT;
    }

    public static Dist dist() {
        return isClient() ? Dist.CLIENT : Dist.DEDICATED_SERVER;
    }

    public static boolean isModLoaded(String modId) {
        return Platform.isModLoaded(modId);
    }

    public static Path configDir() {
        return Platform.getConfigFolder();
    }

    public static Path gameDir() {
        return Platform.getGameFolder();
    }

    /** The running server, or null. Stands in for NeoForge's {@code ServerLifecycleHooks.getCurrentServer()}. */
    public static @Nullable MinecraftServer currentServer() {
        return GameInstance.getServer();
    }
}
