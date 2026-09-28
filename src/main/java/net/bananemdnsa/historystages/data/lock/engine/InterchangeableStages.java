package net.bananemdnsa.historystages.data.lock.engine;

import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.data.StageEntry;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Which stages carry the {@code interchangeable} flag, per scope.
 *
 * <p>Kept apart from the stage maps so {@link LockResolution} can ask it without a store on the
 * classpath: the engine installs the real maps as the source, tests set the answer directly.
 *
 * <p>Per scope rather than one set because nothing stops a global and an individual stage from
 * sharing an id, and the flag on one must not leak into the other's lock question.
 */
public final class InterchangeableStages {

    private static volatile Function<StageScope, Map<String, StageEntry>> source = scope -> Map.of();

    // Null means stale. Rebuilt on the next read, so a load that changes every stage costs one
    // scan rather than one per change.
    private static volatile Set<String> global;
    private static volatile Set<String> individual;

    private InterchangeableStages() {}

    /** Where the stage maps come from. Installed by the engine. */
    public static void source(Function<StageScope, Map<String, StageEntry>> stages) {
        source = stages;
        markDirty();
    }

    /** The stages changed; the next read rescans. */
    public static void markDirty() {
        global = null;
        individual = null;
    }

    /** The flagged stage ids in this scope. Empty in any pack that never uses the flag. */
    public static Set<String> of(StageScope scope) {
        if (scope == StageScope.GLOBAL) {
            Set<String> flagged = global;
            if (flagged == null) global = flagged = scan(scope);
            return flagged;
        }
        Set<String> flagged = individual;
        if (flagged == null) individual = flagged = scan(scope);
        return flagged;
    }

    /** Test hook: fixes the answer until the next {@link #markDirty} or {@link #source}. */
    public static void setForTest(Set<String> globalFlagged, Set<String> individualFlagged) {
        global = Set.copyOf(globalFlagged);
        individual = Set.copyOf(individualFlagged);
    }

    private static Set<String> scan(StageScope scope) {
        Map<String, StageEntry> stages = source.apply(scope);
        Set<String> flagged = null;
        for (Map.Entry<String, StageEntry> entry : stages.entrySet()) {
            if (entry.getValue() != null && entry.getValue().isInterchangeable()) {
                if (flagged == null) flagged = new HashSet<>();
                flagged.add(entry.getKey());
            }
        }
        return flagged == null ? Set.of() : Set.copyOf(flagged);
    }
}
