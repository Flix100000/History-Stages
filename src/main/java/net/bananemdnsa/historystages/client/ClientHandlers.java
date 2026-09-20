package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.client.scroll.OpenScrollHandler;
import net.bananemdnsa.historystages.commands.ClientDebugCommand;
import net.bananemdnsa.historystages.commands.ClientScreenCommand;
import net.bananemdnsa.historystages.events.ToastClickHandler;
import net.bananemdnsa.historystages.events.TooltipEventHandler;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.List;

/**
 * The client half of the handler list, kept apart so a dedicated server never names these classes.
 *
 * <p>See {@code Handlers} for why the list exists at all.
 */
@Environment(EnvType.CLIENT)
public final class ClientHandlers {

    private ClientHandlers() {}

    public static final List<Class<?>> CLIENT = List.of(
            ClientDisconnectHandler.class,
            ClientFluidRecipeIndex.class,
            EditorButtonHandler.class,
            LockBorderRenderer.class,
            LockOverlayRenderer.class,
            OpenScrollHandler.class,
            TradeLockNoticeRenderer.class,
            ZoneBorderRenderer.class,
            ZoneOverlayRenderer.class,
            ZoneSelectionRenderer.class,
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
