package net.bananemdnsa.historystages.mixin.client;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.client.RecipesUpdatedEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Says that the client has been sent a new recipe list.
 *
 * <p>At the end of the handler rather than the start, so whatever reacts is reading the new list
 * and not the one being replaced. The server resends this list after a stage changes, which is
 * what makes a recipe viewer notice an unlock — and anything this mod indexed off recipes has to
 * be built again at the same moment.
 */
@Mixin(ClientPacketListener.class)
public abstract class RecipesUpdatedMixin {

    @Inject(method = "handleUpdateRecipes", at = @At("TAIL"))
    private void historystages$raise(ClientboundUpdateRecipesPacket packet, CallbackInfo ci) {
        EventBus.post(new RecipesUpdatedEvent(Minecraft.getInstance().getConnection().getRecipeManager()));
    }
}
