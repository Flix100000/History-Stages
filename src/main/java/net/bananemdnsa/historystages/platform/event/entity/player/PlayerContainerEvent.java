package net.bananemdnsa.historystages.platform.event.entity.player;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** A player and a menu; see {@code mixin/event/ContainerOpenMixin} for where it is raised. */
public abstract class PlayerContainerEvent extends Event {

    private final Player player;
    private final AbstractContainerMenu container;

    protected PlayerContainerEvent(Player player, AbstractContainerMenu container) {
        this.player = player;
        this.container = container;
    }

    public Player getEntity() {
        return player;
    }

    public AbstractContainerMenu getContainer() {
        return container;
    }

    /** A menu has just been opened and sent to the client. */
    public static class Open extends PlayerContainerEvent {
        public Open(Player player, AbstractContainerMenu container) {
            super(player, container);
        }
    }
}
