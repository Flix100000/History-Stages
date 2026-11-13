package net.bananemdnsa.historystages.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.bananemdnsa.historystages.api.HistoryStagesClientPlugin;
import net.bananemdnsa.historystages.api.HistoryStagesPlugin;

import java.util.function.BiConsumer;

/**
 * How addons join the registration window on each loader.
 *
 * <p>NeoForge: every class another mod marks with the platform {@code @EventBusSubscriber} is found
 * in NeoForge's scan data and registered on the platform bus with that mod as its owner
 * ({@link #discoverSubscribers}), so an addon written for 1.21.1 only swaps imports. Fabric: the
 * {@code historystages} and {@code historystages:client} entrypoints, as on the 1.21.1 Fabric line.
 */
public final class AddonEntrypoints {

    private AddonEntrypoints() {}

    /** Registers other mods' subscriber classes on the platform bus. Nothing to do on Fabric. */
    @ExpectPlatform
    public static void discoverSubscribers() {
        throw new AssertionError();
    }

    /** Calls {@code body} for each {@code historystages} entrypoint with its mod id. None on NeoForge. */
    @ExpectPlatform
    public static void forEachPlugin(BiConsumer<String, HistoryStagesPlugin> body) {
        throw new AssertionError();
    }

    /** As {@link #forEachPlugin}, for {@code historystages:client}. Client only. */
    @ExpectPlatform
    public static void forEachClientPlugin(BiConsumer<String, HistoryStagesClientPlugin> body) {
        throw new AssertionError();
    }
}
