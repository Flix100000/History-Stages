package net.bananemdnsa.historystages.data.graph;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Which stages hang off a given one — what "select with all dependents" picks up. */
public final class GraphBranch {

    private GraphBranch() {}

    /**
     * {@code start} plus every stage that depends on it, directly or through others, across both
     * trees. A stage {@code onMap} rejects is neither selected nor walked through: it cannot be
     * moved, and whatever hangs only off it is not visibly part of this branch.
     *
     * @param prerequisites graph key → the graph keys it depends on, as
     *                      {@code StageManager.graphPrerequisites()} returns them
     */
    public static Set<String> withDependents(String start, Map<String, Set<String>> prerequisites,
                                             Predicate<String> onMap) {
        Set<String> out = new LinkedHashSet<>();
        if (!onMap.test(start)) return out;
        Deque<String> queue = new ArrayDeque<>();
        out.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (Map.Entry<String, Set<String>> e : prerequisites.entrySet()) {
                if (!e.getValue().contains(current)) continue;
                String dependent = e.getKey();
                if (onMap.test(dependent) && out.add(dependent)) queue.add(dependent);
            }
        }
        return out;
    }
}
