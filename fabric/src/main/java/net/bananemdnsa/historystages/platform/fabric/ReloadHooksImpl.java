package net.bananemdnsa.historystages.platform.fabric;

import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.PreparableReloadListener;

/** Fabric side of {@code platform.ReloadHooks}: no condition context to hand over. */
public final class ReloadHooksImpl {

    private ReloadHooksImpl() {}

    public static void beginReplay(MinecraftServer server, List<PreparableReloadListener> listeners) {
    }

    public static void endReplay(List<PreparableReloadListener> listeners) {
    }

    public static void tagsReloaded(MinecraftServer server) {
        CommonLifecycleEvents.TAGS_LOADED.invoker().onTagsLoaded(
                net.bananemdnsa.historystages.reload.FullRegistriesHolder.of(server), false);
    }
}
