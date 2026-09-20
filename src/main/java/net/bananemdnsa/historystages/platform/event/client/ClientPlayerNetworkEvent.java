package net.bananemdnsa.historystages.platform.event.client;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.LocalPlayer;

@Environment(EnvType.CLIENT)
public abstract class ClientPlayerNetworkEvent extends Event {

    private final LocalPlayer player;

    protected ClientPlayerNetworkEvent(LocalPlayer player) {
        this.player = player;
    }

    public LocalPlayer getPlayer() {
        return player;
    }

    /**
     * Leaving a server. Everything this client was told about that server has to go with it, or
     * the next world starts out believing the last one's stages.
     */
    public static class LoggingOut extends ClientPlayerNetworkEvent {
        public LoggingOut(LocalPlayer player) {
            super(player);
        }
    }
}
