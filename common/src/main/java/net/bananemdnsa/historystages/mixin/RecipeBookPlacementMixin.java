package net.bananemdnsa.historystages.mixin;

import net.bananemdnsa.historystages.events.RecipeHandler;
import net.bananemdnsa.historystages.util.lock.LockFeedback;
import net.bananemdnsa.historystages.util.lock.LockMessages;
import net.bananemdnsa.historystages.util.lock.RecipeCraftContext;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Says why nothing happened when a player clicks a locked recipe in the recipe book.
 *
 * <p>The click fills the grid, and the grid then produces no result — not exploitable, only
 * silent. Clicking is a deliberate act, unlike dragging items around, so it earns a line; the
 * crafting preview deliberately gets none, because the grid changes on every item moved and a
 * message per change would be constant noise.
 *
 * <p>Does not cancel the placement. The grid filling up is vanilla's business, and the gate that
 * matters already sits at the resolution.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class RecipeBookPlacementMixin {

    @Shadow public ServerPlayer player;

    private static final String FEEDBACK_CATEGORY = "recipe_book";

    // After the thread hop rather than at HEAD: the packet arrives on the network thread first,
    // and the lock check reads server state.
    @Inject(method = "handlePlaceRecipe", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V"))
    private void historystages$tellThemWhyNothingHappened(ServerboundPlaceRecipePacket packet,
                                                          CallbackInfo ci) {
        // The client sends a display id; the recipe behind it is only known to the server.
        var display = this.player.level().getServer().getRecipeManager().getRecipeFromDisplay(packet.recipe());
        if (display == null) return;
        // The same narrow window a station opens: this question is about this player and this
        // click, and nothing else.
        boolean locked = RecipeCraftContext.with(this.player.getUUID(),
                () -> RecipeHandler.isRecipeIdLocked(display.parent().id().identifier(), false));
        if (!locked) return;
        LockFeedback.sendActionbar(this.player, FEEDBACK_CATEGORY, LockMessages.recipeLocked());
    }
}
