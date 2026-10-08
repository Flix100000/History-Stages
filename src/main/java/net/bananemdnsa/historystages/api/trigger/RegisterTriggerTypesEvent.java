package net.bananemdnsa.historystages.api.trigger;

import net.bananemdnsa.historystages.data.auto.TriggerTypes;

import net.bananemdnsa.historystages.api.trigger.TriggerCondition;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/**
 * Fired once so another mod can add its own auto-trigger type.
 *
 * <p>A trigger says when a stage unlocks by itself. The built-in ones cover entering a biome,
 * crafting an item, killing something and so on; an addon that owns some other notion of progress
 * adds it here and fires it through the same path.
 *
 * <p>Plain registrations are events: they fire once and count from then on. A type that describes
 * something that can stop being true (standing in a zone, a world flag) should register with a
 * {@link TriggerKind} state kind and a class implementing {@link StateTrigger}. Only state kinds
 * can keep a {@code conditional} stage open or be negated in a {@code lock_trigger}; a
 * {@link TriggerKind#WORLD_STATE} is the only kind a global conditional stage accepts.
 *
 * <pre>{@code
 * modEventBus.addListener(RegisterTriggerTypesEvent.class, event -> {
 *     event.register("mymod:relic_found", RelicFoundTrigger.class, StageScope.GLOBAL);
 *     event.register("mymod:in_zone", InZoneTrigger.class, TriggerKind.PLAYER_STATE);
 * });
 * }</pre>
 */
public class RegisterTriggerTypesEvent extends Event implements IModBusEvent {

    public void register(String type, Class<? extends TriggerCondition> conditionClass) {
        TriggerTypes.register(type, conditionClass);
    }

    /**
     * Registers a trigger type restricted to the given scopes. See
     * {@link TriggerTypes#register(String, Class, StageScope...)} for what that means and why
     * both scopes apply by default.
     */
    public void register(String type, Class<? extends TriggerCondition> conditionClass,
                         StageScope... scopes) {
        TriggerTypes.register(type, conditionClass, scopes);
    }

    /**
     * Registers a trigger type of the given kind, restricted to the given scopes (at least one).
     * Any kind is accepted; a state kind ({@link TriggerKind#isState()}) needs a class
     * implementing {@link StateTrigger}, or this throws. See {@link TriggerKind} for what a state is.
     */
    public void register(String type, Class<? extends TriggerCondition> conditionClass,
                         TriggerKind kind, StageScope... scopes) {
        TriggerTypes.register(type, conditionClass, kind, scopes);
    }

    /** Registers a trigger type of the given kind for both global and individual stages. */
    public void register(String type, Class<? extends TriggerCondition> conditionClass, TriggerKind kind) {
        TriggerTypes.register(type, conditionClass, kind);
    }
}
