package net.bananemdnsa.historystages.platform.event;

import com.mojang.brigadier.CommandDispatcher;
import net.bananemdnsa.historystages.platform.bus.Event;
import net.minecraft.commands.CommandSourceStack;

/** The point at which commands may be added to the server's dispatcher. */
public class RegisterCommandsEvent extends Event {

    private final CommandDispatcher<CommandSourceStack> dispatcher;

    public RegisterCommandsEvent(CommandDispatcher<CommandSourceStack> dispatcher) {
        this.dispatcher = dispatcher;
    }

    public CommandDispatcher<CommandSourceStack> getDispatcher() {
        return dispatcher;
    }
}
