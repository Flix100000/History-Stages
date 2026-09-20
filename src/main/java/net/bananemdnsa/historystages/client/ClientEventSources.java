package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.player.ItemTooltipEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;

/**
 * The events that only exist on a client.
 *
 * <p>Its own class for the same reason as the client packet registration: a dedicated server has
 * none of these callbacks, and a class naming them must not be loaded there.
 */
@Environment(EnvType.CLIENT)
public final class ClientEventSources {

    private ClientEventSources() {}

    public static void register() {
        // The list handed over is the live one, which is what the handlers expect: they add lines
        // to it in place rather than answering with a new list.
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) ->
                EventBus.post(new ItemTooltipEvent(stack, lines)));
    }
}
