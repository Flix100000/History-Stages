package net.bananemdnsa.historystages.api.trigger;

/**
 * A trigger that describes a state the player or the world is in, rather than something that
 * happened. Only these can keep a "conditional" stage open or be negated in a lock list, because
 * only these can stop being true.
 *
 * <p>Register the type with {@link TriggerKind#PLAYER_STATE} or {@link TriggerKind#WORLD_STATE};
 * a class registered as a state must implement this interface.
 */
public interface StateTrigger extends TriggerCondition {

    /** Whether the state holds right now, in the given view. Must not change the world. */
    boolean holds(StateView view);
}
