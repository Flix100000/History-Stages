package net.bananemdnsa.historystages.util;

import net.bananemdnsa.historystages.client.ClientRegistryAccessHelper;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Whichever registry set belongs to the world that is actually running.
 *
 * <p>Asked wherever we have to hand a registry set to somebody else's code — most of all to a
 * recipe, which is asked what it produces. A vanilla recipe knows its result by heart and would
 * take anything; a modded one may build the result out of the registries it is given, and handing
 * it an empty set means telling it the world contains nothing. The lucky outcome is that it throws
 * and we treat the recipe as unlocked; the unlucky one is that it remembers the empty answer and
 * repeats it to everyone who asks afterwards.
 *
 * <p>A server is checked first and covers both the dedicated and the integrated case, and it is
 * safe to read from any thread. Only a client with no server of its own — one connected to a
 * remote server — falls back to the registries that were synced to it.
 */
public final class CurrentRegistries {

    private CurrentRegistries() {}

    /**
     * Null before there is a world to ask, which is a real state: data packs are read before the
     * server exists. Callers that hand the answer to foreign code want {@link #orEmpty()}.
     */
    public static RegistryAccess get() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) return server.registryAccess();
        if (FMLEnvironment.dist == Dist.CLIENT) return ClientRegistryAccessHelper.get();
        return null;
    }

    /**
     * The same, with the empty set standing in when there is no world yet.
     *
     * <p>Empty rather than null because the callers pass this straight on: a vanilla recipe handed
     * an empty set still answers correctly, a vanilla recipe handed null throws.
     */
    public static RegistryAccess orEmpty() {
        RegistryAccess registries = get();
        return registries != null ? registries : RegistryAccess.EMPTY;
    }
}
