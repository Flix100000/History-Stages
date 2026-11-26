package net.bananemdnsa.historystages.mixin;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.bananemdnsa.historystages.reload.LightReload;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleReloadInstance;
import net.minecraft.util.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Remembers every listener a datapack reload runs, so a stage change can run just the mods' ones
 * again later instead of reloading everything.
 *
 * <p>The list is taken at the two calls inside {@code create}, not around it: Fabric API adds the
 * listeners mods registered through its resource loader with a {@code @ModifyArg} on exactly those
 * calls. Wrapping the whole method would only ever see vanilla's three there, and a targeted
 * reload would have nothing to run. NeoForge has added its listeners before {@code create}, so the
 * same place sees them on both loaders.
 *
 * <p>Client resource reloads come through here too and are left alone — {@link LightReload} only
 * keeps a list that has a recipe manager in it.
 */
@Mixin(SimpleReloadInstance.class)
public class SimpleReloadInstanceMixin {

    @WrapMethod(method = "create")
    private static ReloadInstance historystages$profileOnRequest(
            ResourceManager resourceManager, List<PreparableReloadListener> listeners,
            Executor backgroundExecutor, Executor gameExecutor, CompletableFuture<Unit> alsoWaitedFor,
            boolean profiled, Operation<ReloadInstance> original) {
        // The lab command asks for a timed full reload; vanilla only profiles with debug logging on.
        boolean profile = profiled || (LightReload.isServerData(listeners) && LightReload.takeProfileRequest());
        return original.call(resourceManager, listeners, backgroundExecutor, gameExecutor,
                alsoWaitedFor, profile);
    }

    @WrapOperation(method = "create", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/packs/resources/SimpleReloadInstance;of(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/List;Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Ljava/util/concurrent/CompletableFuture;)Lnet/minecraft/server/packs/resources/ReloadInstance;"))
    private static ReloadInstance historystages$rememberListeners(
            ResourceManager resourceManager, List<PreparableReloadListener> listeners,
            Executor backgroundExecutor, Executor gameExecutor, CompletableFuture<Unit> alsoWaitedFor,
            Operation<ReloadInstance> original) {
        LightReload.rememberIfServerData(listeners);
        return original.call(resourceManager, listeners, backgroundExecutor, gameExecutor, alsoWaitedFor);
    }

    @WrapOperation(method = "create", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/packs/resources/ProfiledReloadInstance;of(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/List;Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Ljava/util/concurrent/CompletableFuture;)Lnet/minecraft/server/packs/resources/ReloadInstance;"))
    private static ReloadInstance historystages$rememberProfiledListeners(
            ResourceManager resourceManager, List<PreparableReloadListener> listeners,
            Executor backgroundExecutor, Executor gameExecutor, CompletableFuture<Unit> alsoWaitedFor,
            Operation<ReloadInstance> original) {
        LightReload.rememberIfServerData(listeners);
        return original.call(resourceManager, listeners, backgroundExecutor, gameExecutor, alsoWaitedFor);
    }
}
