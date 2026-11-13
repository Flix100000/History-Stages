package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.client.disguise.ClientDisguises;
import net.bananemdnsa.historystages.commands.ClientDebugCommand;
import net.bananemdnsa.historystages.commands.ClientScreenCommand;
import net.bananemdnsa.historystages.events.ToastClickHandler;
import net.bananemdnsa.historystages.events.TooltipEventHandler;
import net.bananemdnsa.historystages.platform.bus.EventBus;

import java.util.List;

/**
 * The client half of the handler list, kept apart so a dedicated server never names these
 * classes. See {@code events.Handlers}. The renderers and the other client subscribers are
 * still in port-pending (Task 11) and join this list when they move.
 */
public final class ClientHandlers {

    private ClientHandlers() {}

    public static final List<Class<?>> CLIENT = List.of(
            ClientFluidRecipeIndex.class,
            ClientDisguises.class,
            ClientDebugCommand.class,
            ClientScreenCommand.class,
            ToastClickHandler.class,
            TooltipEventHandler.class);

    public static void register() {
        for (Class<?> handler : CLIENT) {
            EventBus.register(handler);
        }
    }
}
