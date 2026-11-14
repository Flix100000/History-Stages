package net.bananemdnsa.historystages.client;

import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;

/**
 * Rebuilds the vanilla recipe book after the player's stages changed.
 *
 * <p>{@code ClientRecipeBookMixin} filters the book as it is assembled, and the book is only
 * assembled when the server sends a recipe update — on join, and on the recipe resync that a global
 * lock or unlock triggers. An <em>individual</em> unlock sends no such packet, so without this the
 * newly unlocked recipes would not show up until the player rejoined.
 *
 * <p>Cheap enough to call on every stage sync: it walks the recipe list once, which is the same
 * work the client already does whenever recipes arrive.
 */
public final class ClientRecipeBookRefresh {

    private ClientRecipeBookRefresh() {}

    /** No-op before the player is in a world. */
    public static void rebuild() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        try {
            ClientRecipeBook book = mc.player.getRecipeBook();
            // 26.2: the book is built from the display entries the server sent, and rebuilding
            // the collections already takes the known recipes into account. 1.21.1 had to follow
            // setupCollections with updateKnownRecipes by hand or the book came out empty; that
            // second step is part of rebuildCollections now. The server must still never send a
            // recipe list without the book (see PacketHandler.resyncRecipes).
            book.rebuildCollections();
        } catch (Exception e) {
            // A book that failed to rebuild shows a stale list; it must not take the packet
            // handler down with it.
            net.bananemdnsa.historystages.util.DebugLogger.warn("Recipe Book",
                    "could not rebuild the recipe book after a stage change: " + e);
        }
    }
}
