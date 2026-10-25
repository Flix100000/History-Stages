package net.bananemdnsa.historystages.data.auto.conditions;

import net.bananemdnsa.historystages.api.trigger.StateTrigger;
import net.bananemdnsa.historystages.api.trigger.StateView;

import com.google.gson.annotations.SerializedName;

/**
 * The player's experience level reaching a threshold.
 *
 * <p>Polled rather than hung off {@code PlayerXpEvent.LevelChange}: someone who is already level 40
 * when the stage is written should not have to gain another level before it opens.
 */
public record XpLevelTrigger(@SerializedName("level") int level) implements StateTrigger {

    public int requiredLevel() { return Math.max(0, level); }

    public boolean matches(int currentLevel) { return currentLevel >= requiredLevel(); }

    @Override public boolean holds(StateView view) { return matches(view.xpLevel()); }

    @Override public String type() { return "xp_level"; }

    @Override public long signature() {
        long h = type().hashCode() & 0xFFFFFFFFL;
        return h * FNV_PRIME_64 ^ (Integer.hashCode(requiredLevel()) & 0xFFFFFFFFL);
    }
}
