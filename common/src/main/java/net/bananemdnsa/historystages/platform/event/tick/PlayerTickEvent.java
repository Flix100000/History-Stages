package net.bananemdnsa.historystages.platform.event.tick;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.minecraft.world.entity.player.Player;

public abstract class PlayerTickEvent extends Event {

    private final Player player;

    protected PlayerTickEvent(Player player) {
        this.player = player;
    }

    public Player getEntity() {
        return player;
    }

    /** After the player has ticked. */
    public static class Post extends PlayerTickEvent {
        public Post(Player player) {
            super(player);
        }
    }
}
