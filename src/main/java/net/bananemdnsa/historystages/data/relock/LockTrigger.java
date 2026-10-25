package net.bananemdnsa.historystages.data.relock;

import com.google.gson.annotations.JsonAdapter;
import net.bananemdnsa.historystages.api.trigger.TriggerCondition;
import net.bananemdnsa.historystages.data.auto.AutoTrigger;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code lock_trigger} block: when a DEFAULT or AUTO stage closes again. Same shape as
 * {@code auto_trigger}, plus whether the stage can be earned back afterwards.
 */
@JsonAdapter(LockTriggerAdapter.class)
public class LockTrigger extends AutoTrigger {

    /** Null reads as false: losing a stage for good is the default the user asked for. */
    private Boolean reUnlockable;

    public LockTrigger() { super(); }

    public LockTrigger(String mode, List<TriggerCondition> triggers, Boolean reUnlockable) {
        super(mode, triggers);
        this.reUnlockable = reUnlockable;
    }

    public boolean isReUnlockable() { return Boolean.TRUE.equals(reUnlockable); }

    public Boolean getRawReUnlockable() { return reUnlockable; }

    public void setReUnlockable(boolean value) { this.reUnlockable = value; }

    @Override
    public LockTrigger copy() {
        return new LockTrigger(getRawMode(), new ArrayList<>(getTriggers()), reUnlockable);
    }
}
