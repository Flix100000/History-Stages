package net.bananemdnsa.historystages.data.auto;

import net.bananemdnsa.historystages.api.trigger.StateTrigger;
import net.bananemdnsa.historystages.api.trigger.StateView;
import net.bananemdnsa.historystages.api.trigger.TriggerCondition;

import java.util.Objects;

/**
 * "No longer holds": a state trigger turned around, for lock lists only. Written to the stage
 * file as the inner trigger plus {@code "negate": true}.
 *
 * <p>Deliberately not a {@link StateTrigger} itself: a conditional stage must never accept a
 * negation, and being a StateTrigger would let it slip through every "is this a state?" check.
 * Event matchers ({@code t instanceof EffectTrigger}) never see through the wrapper either, so a
 * negated trigger cannot fire from an event.
 */
public record NegatedTrigger(TriggerCondition inner) implements TriggerCondition {

    /** Keeps "has fire resistance" and "no longer has it" apart in stored progress. */
    private static final long NEGATE_SALT = 0x9E3779B97F4A7C15L;

    public NegatedTrigger {
        Objects.requireNonNull(inner, "inner");
    }

    @Override public String type() { return inner.type(); }

    @Override public long signature() { return inner.signature() ^ NEGATE_SALT; }

    /** True while the inner state does not hold. A negated event never holds. */
    public boolean holds(StateView view) {
        return inner instanceof StateTrigger st && !st.holds(view);
    }

    public static TriggerCondition unwrap(TriggerCondition t) {
        return t instanceof NegatedTrigger n ? n.inner() : t;
    }
}
