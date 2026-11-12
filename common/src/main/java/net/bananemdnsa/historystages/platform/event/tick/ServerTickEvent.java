package net.bananemdnsa.historystages.platform.event.tick;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.minecraft.server.MinecraftServer;

public abstract class ServerTickEvent extends Event {

    private final MinecraftServer server;

    protected ServerTickEvent(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer getServer() {
        return server;
    }

    /** After the server has ticked, which is where the deferred lock reload is carried out. */
    public static class Post extends ServerTickEvent {
        public Post(MinecraftServer server) {
            super(server);
        }
    }
}
