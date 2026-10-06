package net.bananemdnsa.historystages.data.graph;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GraphBranchTest {

    @Test
    void collectsEveryTransitiveDependent() {
        Map<String, Set<String>> prereq = Map.of(
                "g:a", Set.of(),
                "g:b", Set.of("g:a"),
                "g:c", Set.of("g:b"),
                "g:x", Set.of());

        assertEquals(Set.of("g:a", "g:b", "g:c"), GraphBranch.withDependents("g:a", prereq, k -> true));
    }

    @Test
    void stopsAtStagesThatAreNotOnTheMap() {
        Map<String, Set<String>> prereq = Map.of(
                "g:a", Set.of(),
                "g:b", Set.of("g:a"),
                "g:c", Set.of("g:b"));

        assertEquals(Set.of("g:a"), GraphBranch.withDependents("g:a", prereq, k -> !k.equals("g:b")),
                "a branch reached only through an unplaced stage is not selected");
    }

    @Test
    void crossesIntoTheOtherTree() {
        Map<String, Set<String>> prereq = Map.of(
                "g:a", Set.of(),
                "i:b", Set.of("g:a"));

        assertEquals(Set.of("g:a", "i:b"), GraphBranch.withDependents("g:a", prereq, k -> true));
    }

    @Test
    void survivesACycle() {
        Map<String, Set<String>> prereq = Map.of(
                "g:a", Set.of("g:b"),
                "g:b", Set.of("g:a"));

        assertEquals(Set.of("g:a", "g:b"), GraphBranch.withDependents("g:a", prereq, k -> true));
    }

    @Test
    void startOffTheMapSelectsNothing() {
        assertTrue(GraphBranch.withDependents("g:a", Map.of("g:a", Set.of()), k -> false).isEmpty());
    }
}
