package net.bananemdnsa.historystages.data.dependency;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.data.DependencyGroup;

/**
 * A stage that lists itself as a prerequisite can never be researched: the requirement is only met
 * once the stage is already unlocked. The editor no longer offers it, but a hand-edited file or one
 * saved by an older version can still carry it, so loading drops it.
 */
public final class SelfDependencies {

    private SelfDependencies() {}

    /**
     * Removes every reference to {@code stageId} from its own groups, in the list of its own kind:
     * a global stage from the global-stage list, an individual one from the individual list.
     * Returns how many were removed.
     */
    public static int strip(String stageId, boolean individual, List<DependencyGroup> groups) {
        if (stageId == null || groups == null) return 0;
        int removed = 0;
        for (DependencyGroup group : groups) {
            // Copied rather than edited in place: the lists may have come in immutable.
            if (individual) {
                List<IndividualStageDep> deps = new ArrayList<>(group.getIndividualStages());
                if (deps.removeIf(dep -> stageId.equals(dep.getStageId()))) {
                    removed += group.getIndividualStages().size() - deps.size();
                    group.setIndividualStages(deps);
                }
            } else {
                List<String> stages = new ArrayList<>(group.getStages());
                if (stages.removeIf(stageId::equals)) {
                    removed += group.getStages().size() - stages.size();
                    group.setStages(stages);
                }
            }
        }
        return removed;
    }
}
