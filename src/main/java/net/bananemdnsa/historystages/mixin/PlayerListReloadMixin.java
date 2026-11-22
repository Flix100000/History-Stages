package net.bananemdnsa.historystages.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;

import net.bananemdnsa.historystages.reload.BackgroundReload;
import net.bananemdnsa.historystages.reload.LightReload;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.ServerRecipeBook;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * PROTOTYPE. Keeps the recipe list home at the end of a reload a stage change started.
 *
 * <p>A datapack reload ends by sending every client the recipe list and their recipe book. A
 * client that receives the list throws its recipe data away and rebuilds it, and JEI restarts
 * from scratch on it — on a large pack that restart is the freeze a player actually sees. After a
 * stage change the list is the same list, so there is nothing for the client to take in; JEI is
 * brought up to date by its own hide pass on the stage sync instead.
 *
 * <p>Only for reloads {@link BackgroundReload} started, and only while the lab switch says so.
 * A {@code /reload} by an admin still sends everything, since files may have changed then.
 * The list and the book go together or not at all, never one without the other — the list alone
 * leaves the vanilla recipe book empty until the player rejoins.
 */
@Mixin(PlayerList.class)
public class PlayerListReloadMixin {

    @Shadow @Final private MinecraftServer server;

    @WrapWithCondition(method = "reloadResources", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private boolean historystages$keepRecipeListHome(ServerGamePacketListenerImpl connection, Packet<?> packet) {
        return sendsRecipes();
    }

    @WrapWithCondition(method = "reloadResources", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/stats/ServerRecipeBook;sendInitialRecipeBook(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private boolean historystages$keepRecipeBookHome(ServerRecipeBook book, ServerPlayer player) {
        return sendsRecipes();
    }

    private boolean sendsRecipes() {
        return LightReload.resendRecipes() || !BackgroundReload.isRunning(server);
    }
}
