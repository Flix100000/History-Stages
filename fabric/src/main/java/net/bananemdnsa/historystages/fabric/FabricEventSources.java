package net.bananemdnsa.historystages.fabric;

import net.bananemdnsa.historystages.fabric.client.ClientBridge;
import net.bananemdnsa.historystages.fabric.events.InteractionBridge;
import net.bananemdnsa.historystages.fabric.events.LifecycleBridge;

/**
 * Feeds Fabric's callbacks into the platform bus.
 *
 * <p>Events with a Fabric callback are bridged in {@code events/}, one listener per priority in
 * matching callback phases (see {@code Phases}). The rest have no callback and are raised from the
 * mixins in {@code mixin/event}, which post every priority in order at the mixin point. The
 * right-click-on-a-block event is both: raised from the callback, its two "may the block / the
 * item be used" answers enforced by mixins.
 */
public final class FabricEventSources {

    private FabricEventSources() {}

    public static void register() {
        InteractionBridge.register();
        LifecycleBridge.register();
    }

    /** Only from the client entrypoint; the bridge names client classes a dedicated server lacks. */
    public static void registerClient() {
        ClientBridge.register();
    }
}
