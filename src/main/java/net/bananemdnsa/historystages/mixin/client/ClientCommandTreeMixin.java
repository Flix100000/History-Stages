package net.bananemdnsa.historystages.mixin.client;

import net.bananemdnsa.historystages.client.ClientCommandTree;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Repairs the command tree after the server's copy of it has been built.
 *
 * <p>The priority is raised so this runs after the loader's own hook on the same method, which is
 * what copies the client commands in: repairing the copy before it happens would do nothing.
 *
 * @see ClientCommandTree
 */
@Environment(EnvType.CLIENT)
@Mixin(value = ClientPacketListener.class, priority = 1500)
public abstract class ClientCommandTreeMixin {

    @Inject(method = "handleCommands", at = @At("TAIL"))
    private void historystages$graftClientCommands(ClientboundCommandsPacket packet, CallbackInfo ci) {
        ClientCommandTree.graftOnto(((ClientPacketListener) (Object) this).getCommands());
    }
}
