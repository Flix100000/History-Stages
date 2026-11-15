package net.bananemdnsa.historystages.data.dependency;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.data.DependencyGroup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A stage that names itself as a prerequisite could never be researched, so loading drops it. */
class SelfDependenciesTest {

    private static DependencyGroup group(List<String> stages, List<IndividualStageDep> individual) {
        DependencyGroup group = new DependencyGroup();
        group.setStages(stages);
        group.setIndividualStages(individual);
        return group;
    }

    @Test
    void anIndividualStageLosesItselfAndKeepsTheRest() {
        DependencyGroup group = group(new ArrayList<>(), List.of(
                new IndividualStageDep("indiv", IndividualStageDep.MODE_PLAYER),
                new IndividualStageDep("other", IndividualStageDep.MODE_ALL_ONLINE)));

        assertEquals(1, SelfDependencies.strip("indiv", true, List.of(group)));
        assertEquals(List.of("other"),
                group.getIndividualStages().stream().map(IndividualStageDep::getStageId).toList());
    }

    @Test
    void aGlobalStageLosesItselfFromEveryGroup() {
        DependencyGroup first = group(List.of("eisen", "stein"), List.of());
        DependencyGroup second = group(List.of("eisen"), List.of());

        assertEquals(2, SelfDependencies.strip("eisen", false, List.of(first, second)));
        assertEquals(List.of("stein"), first.getStages());
        assertEquals(List.of(), second.getStages());
    }

    /** Only the list of its own kind is touched. */
    @Test
    void theOtherScopeIsLeftAlone() {
        DependencyGroup group = group(List.of("same"), List.of(
                new IndividualStageDep("same", IndividualStageDep.MODE_PLAYER)));

        assertEquals(1, SelfDependencies.strip("same", true, List.of(group)));
        assertEquals(List.of("same"), group.getStages());
    }
}
