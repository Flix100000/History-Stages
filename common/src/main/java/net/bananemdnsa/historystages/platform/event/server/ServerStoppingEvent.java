package net.bananemdnsa.historystages.platform.event.server;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.minecraft.server.MinecraftServer;

/** The server on its way down, while its data can still be written out. */
public class ServerStoppingEvent extends Event {

    private final MinecraftServer server;

    public ServerStoppingEvent(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer getServer() {
        return server;
    }
}
