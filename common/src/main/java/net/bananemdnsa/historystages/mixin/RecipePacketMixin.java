package net.bananemdnsa.historystages.mixin;

import net.bananemdnsa.historystages.util.lock.StonecutterGate;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Every recipe packet leaves with the stonecutter list filtered for the player it goes to: on
 * join, after a datapack reload ({@code PlayerList.reloadResources} sends one shared packet to
 * everyone) and on our own resyncs. One place, so no sender can forget it.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public class RecipePacketMixin {

    @ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
            at = @At("HEAD"), argsOnly = true)
    private Packet<?> historystages$perPlayerRecipes(Packet<?> packet) {
        if (packet instanceof ClientboundUpdateRecipesPacket recipes
                && (Object) this instanceof ServerGamePacketListenerImpl game && game.player != null) {
            return StonecutterGate.forPlayer(game.player, recipes);
        }
        return packet;
    }
}
