package net.bananemdnsa.historystages.util.lock;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.events.RecipeHandler;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * Which recipes a player's client is told about. The server sends each player their own
 * recipe book (display entries) and stonecutter list, so locked recipes are left out there; the
 * book itself (the known ids saved with the player) is never touched.
 *
 * <p>A stage change marks the player; the end of the server tick re-sends the stonecutter list
 * and the book once per marked player (a pedestal completion syncs stages two or three times).
 * Vanilla always sends the two together: the client rebuilds its book collections on the list.
 */
public final class RecipeBookGate {

    private static final Set<ServerPlayer> DIRTY = Collections.newSetFromMap(new WeakHashMap<>());
    private static Boolean lastHideSetting;

    private RecipeBookGate() {}

    /** Whether this recipe stays out of the player's book. */
    public static boolean hides(ServerPlayer player, ResourceKey<Recipe<?>> id) {
        if (!Config.VISUAL.hideLockedRecipesInBook.get()) return false;
        MinecraftServer server = player.level().getServer();
        RecipeHolder<?> holder = server.getRecipeManager().byKey(id).orElse(null);
        return RecipeCraftContext.with(player.getUUID(), () ->
                RecipeHandler.isRecipeIdLocked(id.identifier(), false)
                        || (holder != null && RecipeHandler.isOutputLocked(holder, false)));
    }

    /** The player's stages changed; their book and stonecutter list go out again this tick. */
    public static synchronized void resend(ServerPlayer player) {
        DIRTY.add(player);
    }

    public static synchronized void resendAll(MinecraftServer server) {
        if (server == null) return;
        DIRTY.addAll(server.getPlayerList().getPlayers());
    }

    /** For tests: whether this player waits for a resend. */
    public static synchronized boolean pending(ServerPlayer player) {
        return DIRTY.contains(player);
    }

    /** End of the server tick: one send per marked player; a toggled book setting marks everyone. */
    public static void flush(MinecraftServer server) {
        boolean hide = Config.VISUAL.hideLockedRecipesInBook.get();
        if (lastHideSetting != null && lastHideSetting != hide) resendAll(server);
        lastHideSetting = hide;

        ServerPlayer[] players;
        synchronized (RecipeBookGate.class) {
            if (DIRTY.isEmpty()) return;
            players = DIRTY.toArray(new ServerPlayer[0]);
            DIRTY.clear();
        }
        RecipeManager recipes = server.getRecipeManager();
        for (ServerPlayer player : players) {
            if (player.connection == null || player.hasDisconnected()) continue;
            // Filtered per player on the way out (StonecutterGate, RecipePacketMixin).
            player.connection.send(new ClientboundUpdateRecipesPacket(
                    recipes.getSynchronizedItemProperties(), recipes.getSynchronizedStonecutterRecipes()));
            player.getRecipeBook().sendInitialRecipeBook(player);
        }
    }
}
