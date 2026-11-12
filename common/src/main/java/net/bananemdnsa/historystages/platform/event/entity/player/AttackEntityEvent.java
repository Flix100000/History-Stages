package net.bananemdnsa.historystages.platform.event.entity.player;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.bananemdnsa.historystages.platform.bus.ICancellableEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** A player swinging at something. Cancelling means the blow never lands. */
public class AttackEntityEvent extends Event implements ICancellableEvent {

    private final Player player;
    private final Entity target;

    public AttackEntityEvent(Player player, Entity target) {
        this.player = player;
        this.target = target;
    }

    public Player getEntity() {
        return player;
    }

    public Entity getTarget() {
        return target;
    }
}
