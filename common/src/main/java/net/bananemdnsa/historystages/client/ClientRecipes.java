package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.data.lock.FluidRecipeIndex;
import net.bananemdnsa.historystages.network.GzipJson;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.RecipeListSync;
import net.bananemdnsa.historystages.network.serverbound.RequestRecipeListPacket;
import net.bananemdnsa.historystages.util.AllRecipesCache;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * The recipe list the editor reads. Since 1.21.2 a client gets no recipes from the server. With a
 * server of its own (singleplayer, LAN host) the client reads that one; on a dedicated server the
 * editor asks for the list ({@code RecipeListSync}) and it is kept here for that connection.
 */
public final class ClientRecipes {

    private static List<RecipeHolder<?>> serverList = List.of();
    // The connection the list belongs to; a new one starts without.
    private static ClientPacketListener listFor;
    private static int version;

    private ClientRecipes() {}

    public static Collection<RecipeHolder<?>> all() {
        Minecraft mc = Minecraft.getInstance();
        if (listFor != null && listFor == mc.getConnection()) return serverList;
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) return List.of();
        // The unfiltered snapshot is the integrated server's, so it only counts with one.
        Collection<RecipeHolder<?>> cached = AllRecipesCache.get();
        return cached.isEmpty() ? server.getRecipeManager().getRecipes() : cached;
    }

    /** Changes whenever a server list arrives, so a screen that built from an empty one rebuilds. */
    public static int version() {
        return version;
    }

    /**
     * Called when the editor opens: asks a dedicated server for its list as long as this connection
     * has none. A player made operator later gets it on the next open; the server checks the
     * permission every time.
     */
    public static void requestIfNeeded() {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        if (connection == null || mc.getSingleplayerServer() != null || listFor == connection) return;
        PacketHandler.sendToServer(new RequestRecipeListPacket());
    }

    public static void acceptServerList(byte[] gzippedJson) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getConnection() == null) return;
        List<RecipeHolder<?>> recipes;
        int[] skipped = new int[1];
        try {
            recipes = RecipeListSync.fromJson(GzipJson.decompress(gzippedJson, RecipeListSync.MAX_JSON_CHARS),
                    mc.level.registryAccess(), skipped);
        } catch (IOException | RuntimeException e) {
            DebugLogger.warn("Editor Recipes", "could not read the server's recipe list: " + e);
            return;
        }
        if (skipped[0] > 0) {
            DebugLogger.warn("Editor Recipes", skipped[0] + " recipes from the server could not be read and are left out");
        }
        serverList = List.copyOf(recipes);
        listFor = mc.getConnection();
        version++;
        // The fluid index is built from this list too; it was empty until now.
        FluidRecipeIndex.markDirty();
        ClientFluidRecipeIndex.refresh();
    }
}
