package net.bananemdnsa.historystages.platform.event.client;

import com.mojang.brigadier.CommandDispatcher;
import net.bananemdnsa.historystages.platform.bus.Event;
import net.minecraft.commands.CommandSourceStack;

/**
 * Where commands that never leave the client are added.
 *
 * <p>The source type is the vanilla one, as on NeoForge, so the client commands written for that
 * loader carry over unchanged. A loader whose client command API hands out its own source type
 * has to adapt it in its bridge.
 */
public class RegisterClientCommandsEvent extends Event {

    private final CommandDispatcher<CommandSourceStack> dispatcher;

    public RegisterClientCommandsEvent(CommandDispatcher<CommandSourceStack> dispatcher) {
        this.dispatcher = dispatcher;
    }

    public CommandDispatcher<CommandSourceStack> getDispatcher() {
        return dispatcher;
    }
}
