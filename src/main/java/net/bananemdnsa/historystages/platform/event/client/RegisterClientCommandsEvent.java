package net.bananemdnsa.historystages.platform.event.client;

import com.mojang.brigadier.CommandDispatcher;
import net.bananemdnsa.historystages.platform.bus.Event;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * Where commands that never leave the client are added.
 *
 * <p>The source is this loader's own rather than the vanilla one. A client command has no server
 * behind it, so there is no permission level to ask about and feedback goes straight to the chat
 * rather than back through a command source that would have to reach one.
 */
@Environment(EnvType.CLIENT)
public class RegisterClientCommandsEvent extends Event {

    private final CommandDispatcher<FabricClientCommandSource> dispatcher;

    public RegisterClientCommandsEvent(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        this.dispatcher = dispatcher;
    }

    public CommandDispatcher<FabricClientCommandSource> getDispatcher() {
        return dispatcher;
    }
}
