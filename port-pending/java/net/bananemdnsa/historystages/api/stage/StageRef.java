package net.bananemdnsa.historystages.api.stage;

import org.jetbrains.annotations.Nullable;

/**
 * A stage named together with its tree. A global and an individual stage may share an id — they
 * are separate files in separate folders — so wherever both trees meet, as in
 * {@link StageGraph}, an id alone is not enough.
 */
public record StageRef(String id, StageScope scope) {

    /** The ref for a bare id, global first like {@link StageStates#scopeOf}; null if no stage has it. */
    @Nullable
    public static StageRef of(String stageId) {
        StageScope scope = StageStates.scopeOf(stageId);
        return scope == null ? null : new StageRef(stageId, scope);
    }

    public static StageRef global(String stageId) {
        return new StageRef(stageId, StageScope.GLOBAL);
    }

    public static StageRef individual(String stageId) {
        return new StageRef(stageId, StageScope.INDIVIDUAL);
    }

    public boolean isIndividual() {
        return scope == StageScope.INDIVIDUAL;
    }
}
