package net.bananemdnsa.historystages.platform.fabric;

import net.bananemdnsa.historystages.api.HistoryStagesClientPlugin;
import net.bananemdnsa.historystages.api.HistoryStagesPlugin;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

import java.util.function.BiConsumer;

/** Fabric side of {@code platform.AddonEntrypoints}: the entrypoints from the 1.21.1 Fabric line. */
public final class AddonEntrypointsImpl {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final String TEST_PACKAGE = "net.bananemdnsa.historystages.fabric.gametest.";

    private AddonEntrypointsImpl() {}

    public static void discoverSubscribers() {
        // Fabric has no annotation scan; addons declare entrypoints instead.
    }

    public static void forEachPlugin(BiConsumer<String, HistoryStagesPlugin> body) {
        each("historystages", HistoryStagesPlugin.class, body);
    }

    public static void forEachClientPlugin(BiConsumer<String, HistoryStagesClientPlugin> body) {
        each("historystages:client", HistoryStagesClientPlugin.class, body);
    }

    // Containers rather than bare instances: each names the mod that provides it, which is the
    // owner the debug report shows. The call runs in that mod's context (LoaderContextImpl.runAs),
    // so anything it registers on the bus is owned by it too.
    private static <T> void each(String key, Class<T> type, BiConsumer<String, T> body) {
        for (EntrypointContainer<T> container : FabricLoader.getInstance().getEntrypointContainers(key, type)) {
            String modId = container.getProvider().getMetadata().getId();
            // The GameTest plugin ships in the jar with the other test entrypoints; outside a
            // development run it stays out of the registration.
            if (!FabricLoader.getInstance().isDevelopmentEnvironment()
                    && container.getDefinition().startsWith(TEST_PACKAGE)) {
                continue;
            }
            // One broken addon must not cost the others their registration; creating the
            // entrypoint is where a missing class shows up.
            try {
                T plugin = container.getEntrypoint();
                LoaderContextImpl.runAs(modId, () -> body.accept(modId, plugin));
            } catch (RuntimeException | LinkageError e) {
                LOGGER.error("[HistoryStages] {} entrypoint of {} failed", key, modId, e);
            }
        }
    }
}
