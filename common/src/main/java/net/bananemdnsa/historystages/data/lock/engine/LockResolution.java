package net.bananemdnsa.historystages.data.lock.engine;

import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.stage.StageStateView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Combines "which stages gate this subject" with "what this viewer has unlocked".
 *
 * <p>Two policies exist, and which one applies is a caller decision, not an engine one:
 * STRICT (the mod's default) and LENIENT (used by JEI/EMI hiding when
 * {@code Config.VISUAL.lockedItemMultiStagePolicy == LENIENT} — a subject counts as available as
 * soon as any one of its gating stages is unlocked).
 *
 * <p>STRICT is where interchangeable stages come in. Every gating stage without the flag has to
 * be unlocked; the flagged ones form a single OR group, so one of them is enough. A pack that
 * never sets the flag gets the plain "one missing stage locks it" it always had.
 *
 * <p>The scope is a parameter because the flag is read per scope — see
 * {@link InterchangeableStages}.
 */
public final class LockResolution {

    private LockResolution() {}

    /** STRICT, single scope. */
    public static boolean isLocked(StageScope scope, Collection<String> gatingStages, StageStateView state) {
        if (gatingStages.isEmpty()) return false;
        return isLocked(gatingStages, state, InterchangeableStages.of(scope));
    }

    /** STRICT against an explicit flagged set. The rule itself; everything else delegates here. */
    public static boolean isLocked(Collection<String> gatingStages, StageStateView state, Set<String> flagged) {
        boolean anyFlagged = false;
        boolean groupMet = false;
        for (String stage : gatingStages) {
            boolean unlocked = state.isUnlocked(stage);
            if (flagged.contains(stage)) {
                anyFlagged = true;
                if (unlocked) groupMet = true;
            } else if (!unlocked) {
                return true;
            }
        }
        return anyFlagged && !groupMet;
    }

    /** STRICT across both scopes. Equivalent to OR-ing the two single-scope answers. */
    public static boolean isLocked(Collection<String> globalGating, StageStateView globalState,
                                   Collection<String> individualGating, StageStateView individualState) {
        return isLocked(StageScope.GLOBAL, globalGating, globalState)
                || isLocked(StageScope.INDIVIDUAL, individualGating, individualState);
    }

    /**
     * LENIENT across both scopes: an ungated subject is never locked, and a gated one is locked
     * only when none of its gating stages — in either scope — is unlocked.
     */
    public static boolean isLockedLenient(Collection<String> globalGating, StageStateView globalState,
                                          Collection<String> individualGating, StageStateView individualState) {
        if (globalGating.isEmpty() && individualGating.isEmpty()) return false;
        for (String stage : globalGating) {
            if (globalState.isUnlocked(stage)) return false;
        }
        for (String stage : individualGating) {
            if (individualState.isUnlocked(stage)) return false;
        }
        return true;
    }

    /**
     * The stages still standing between this viewer and the subject, in the order the engine
     * returned them.
     *
     * <p>A flagged stage is listed only while its group is still open — once one of them is
     * unlocked the rest no longer stand in the way. That keeps {@code missingStages(...).isEmpty()}
     * equal to {@code !isLocked(...)}, which is how most callers use it.
     */
    public static List<String> missingStages(StageScope scope, Collection<String> gatingStages,
                                             StageStateView state) {
        if (gatingStages.isEmpty()) return List.of();
        Set<String> flagged = InterchangeableStages.of(scope);
        boolean groupMet = !flagged.isEmpty() && anyUnlockedFlagged(gatingStages, state, flagged);
        List<String> missing = new ArrayList<>();
        for (String stage : gatingStages) {
            if (state.isUnlocked(stage)) continue;
            if (groupMet && flagged.contains(stage)) continue;
            missing.add(stage);
        }
        return missing;
    }

    /**
     * The same answer split for display: the stages that are each required, and the open OR group
     * of which any one would do. {@code anyOf} is empty when there are no flagged stages or one of
     * them is already unlocked.
     */
    public static Missing missing(StageScope scope, Collection<String> gatingStages, StageStateView state) {
        if (gatingStages.isEmpty()) return Missing.NONE;
        Set<String> flagged = InterchangeableStages.of(scope);
        boolean groupMet = !flagged.isEmpty() && anyUnlockedFlagged(gatingStages, state, flagged);
        List<String> required = new ArrayList<>();
        List<String> anyOf = new ArrayList<>();
        for (String stage : gatingStages) {
            if (state.isUnlocked(stage)) continue;
            if (flagged.contains(stage)) {
                if (!groupMet) anyOf.add(stage);
            } else {
                required.add(stage);
            }
        }
        return new Missing(required, anyOf);
    }

    private static boolean anyUnlockedFlagged(Collection<String> gatingStages, StageStateView state,
                                              Set<String> flagged) {
        for (String stage : gatingStages) {
            if (flagged.contains(stage) && state.isUnlocked(stage)) return true;
        }
        return false;
    }

    /** What is still missing, split into "each of these" and "any one of these". */
    public record Missing(List<String> required, List<String> anyOf) {
        public static final Missing NONE = new Missing(List.of(), List.of());

        public boolean isEmpty() {
            return required.isEmpty() && anyOf.isEmpty();
        }
    }
}
