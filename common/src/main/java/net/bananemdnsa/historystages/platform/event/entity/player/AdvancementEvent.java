package net.bananemdnsa.historystages.platform.event.entity.player;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.world.entity.player.Player;

public abstract class AdvancementEvent extends Event {

    private final Player player;
    private final AdvancementHolder advancement;

    protected AdvancementEvent(Player player, AdvancementHolder advancement) {
        this.player = player;
        this.advancement = advancement;
    }

    public Player getEntity() {
        return player;
    }

    public AdvancementHolder getAdvancement() {
        return advancement;
    }

    /** Fired once the advancement is actually complete, not when a criterion ticks over. */
    public static class AdvancementEarnEvent extends AdvancementEvent {
        public AdvancementEarnEvent(Player player, AdvancementHolder advancement) {
            super(player, advancement);
        }
    }
}
