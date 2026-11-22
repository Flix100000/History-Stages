package net.bananemdnsa.historystages.mixin;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.bananemdnsa.historystages.reload.LightReload;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleReloadInstance;
import net.minecraft.util.Unit;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Remembers every listener a datapack reload runs, so a stage change can run just the mods' ones
 * again later instead of reloading everything.
 *
 * <p>Hooked here rather than on the loader's own reload event because this is the one place where
 * the finished list goes by on every loader: vanilla's four, the ones added through NeoForge's
 * event, and the ones mods slip in with their own mixins. Same method on 1.20.1 and 1.21.1, which
 * is what keeps a port small.
 *
 * <p>Client resource reloads come through here too and are left alone — {@link LightReload} only
 * keeps a list that has a recipe manager in it.
 */
@Mixin(SimpleReloadInstance.class)
public class SimpleReloadInstanceMixin {

    @WrapMethod(method = "create")
    private static ReloadInstance historystages$rememberServerListeners(
            ResourceManager resourceManager, List<PreparableReloadListener> listeners,
            Executor backgroundExecutor, Executor gameExecutor, CompletableFuture<Unit> alsoWaitedFor,
            boolean profiled, Operation<ReloadInstance> original) {
        boolean serverData = LightReload.rememberIfServerData(listeners);
        // The lab command asks for a timed full reload; vanilla only profiles with debug logging on.
        boolean profile = profiled || (serverData && LightReload.takeProfileRequest());
        return original.call(resourceManager, listeners, backgroundExecutor, gameExecutor,
                alsoWaitedFor, profile);
    }
}
