package net.bananemdnsa.historystages.platform.event.entity.player;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/** A dropped item about to be, or just having been, picked up. */
public abstract class ItemEntityPickupEvent extends Event {

    private final Player player;
    private final ItemEntity itemEntity;

    protected ItemEntityPickupEvent(Player player, ItemEntity itemEntity) {
        this.player = player;
        this.itemEntity = itemEntity;
    }

    public Player getPlayer() {
        return player;
    }

    public ItemEntity getItemEntity() {
        return itemEntity;
    }

    /** Asked before the pickup, and where a lock refuses it. */
    public static class Pre extends ItemEntityPickupEvent {

        private TriState canPickup = TriState.DEFAULT;

        public Pre(Player player, ItemEntity itemEntity) {
            super(player, itemEntity);
        }

        public TriState canPickup() {
            return canPickup;
        }

        public void setCanPickup(TriState value) {
            this.canPickup = value;
        }
    }

    /** After the fact, for anything that only wants to know it happened. */
    public static class Post extends ItemEntityPickupEvent {
        public Post(Player player, ItemEntity itemEntity) {
            super(player, itemEntity);
        }
    }
}
