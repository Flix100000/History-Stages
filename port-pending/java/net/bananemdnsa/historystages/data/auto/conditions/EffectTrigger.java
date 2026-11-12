package net.bananemdnsa.historystages.data.auto.conditions;

import net.bananemdnsa.historystages.api.trigger.StateTrigger;
import net.bananemdnsa.historystages.api.trigger.StateView;

import com.google.gson.annotations.SerializedName;

/**
 * A status effect being applied to the player.
 *
 * <p>Event-driven, not polled: this fires when the effect is <em>given</em>. Someone who already
 * has it when the stage is written waits for the next application, which is the honest reading of
 * "gets the effect" and keeps the trigger on a single code path.
 *
 * <p>{@link #holds} is the other reading, "has the effect right now". Only conditional stages
 * and negated lock triggers ask it; unlocking an auto stage still goes through the event.
 */
public record EffectTrigger(@SerializedName("id") String id) implements StateTrigger {
    @Override public boolean holds(StateView view) { return id != null && view.effects().contains(id); }

    @Override public String type() { return "effect"; }
    @Override public long signature() { return defaultSignature(id); }
}
