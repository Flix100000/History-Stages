package net.bananemdnsa.historystages.platform;

import java.util.List;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.PreparableReloadListener;

/** What a loader adds around a datapack reload, for the targeted reload that replays one. */
public final class ReloadHooks {

    private ReloadHooks() {}

    /**
     * Before replaying {@code listeners}: NeoForge hands its context-aware listeners the
     * reload's condition context. Fabric has nothing to hand over.
     */
    @ExpectPlatform
    public static void beginReplay(MinecraftServer server, List<PreparableReloadListener> listeners) {
        throw new AssertionError();
    }

    /** After the replay, finished or failed: takes the context away again, as a real reload does. */
    @ExpectPlatform
    public static void endReplay(List<PreparableReloadListener> listeners) {
        throw new AssertionError();
    }

    /**
     * Tells mods the server's tags are loaded (NeoForge {@code TagsUpdatedEvent}, Fabric
     * {@code CommonLifecycleEvents.TAGS_LOADED}). Some build their recipe caches there.
     */
    @ExpectPlatform
    public static void tagsReloaded(MinecraftServer server) {
        throw new AssertionError();
    }
}
