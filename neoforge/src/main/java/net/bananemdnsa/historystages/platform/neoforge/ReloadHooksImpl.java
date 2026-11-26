package net.bananemdnsa.historystages.platform.neoforge;

import java.util.List;

import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.resource.ContextAwareReloadListener;

/** NeoForge side of {@code platform.ReloadHooks}. */
public final class ReloadHooksImpl {

    private ReloadHooksImpl() {}

    public static void beginReplay(MinecraftServer server, List<PreparableReloadListener> listeners) {
        ReloadableServerResources managers = server.getServerResources().managers();
        inject(listeners, managers.getConditionContext(), managers.getRegistryLookup());
    }

    public static void endReplay(List<PreparableReloadListener> listeners) {
        inject(listeners, ICondition.IContext.EMPTY, RegistryAccess.EMPTY);
    }

    public static void tagsReloaded(MinecraftServer server) {
        NeoForge.EVENT_BUS.post(new TagsUpdatedEvent.ServerDataLoad(
                server.getServerResources().managers(),
                net.bananemdnsa.historystages.reload.FullRegistriesHolder.of(server)));
    }

    private static void inject(List<PreparableReloadListener> listeners, ICondition.IContext context,
                               net.minecraft.core.HolderLookup.Provider lookup) {
        for (PreparableReloadListener listener : listeners) {
            if (listener instanceof ContextAwareReloadListener aware) aware.injectContext(context, lookup);
        }
    }
}
